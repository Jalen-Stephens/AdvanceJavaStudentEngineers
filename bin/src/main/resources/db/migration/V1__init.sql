-- V1__init.sql
-- Baseline schema using Supabase Auth (auth.users) as identity.
-- No local "users" table. Ownership is tied to JWT sub via auth.uid().

-- 0) Ensure required extension for gen_random_uuid()
create extension if not exists pgcrypto;

-- 1) Status enum for reports (safer than free-form text)
do $$
begin
  if not exists (select 1 from pg_type where typname = 'report_status') then
    create type report_status as enum ('PENDING', 'RUNNING', 'DONE', 'FAILED');
  end if;
end$$;

-- 2) IMAGES: rows owned by Supabase auth user (auth.users.id)
create table if not exists images (
  id            uuid primary key default gen_random_uuid(),
  user_id       uuid not null references auth.users(id) on delete cascade,
  filename      text not null,
  storage_path  text,
  labels        text[],
  note          text,
  uploaded_at   timestamptz not null default now()
);

-- Helpful index for "my images" sorted by newest first
create index if not exists idx_images_user_uploaded
  on images(user_id, uploaded_at desc);

-- 3) ANALYSIS REPORTS: linked to images (cascade on image delete)
create table if not exists analysis_reports (
  id          uuid primary key default gen_random_uuid(),
  image_id    uuid not null references images(id) on delete cascade,
  status      report_status not null default 'PENDING',
  confidence  double precision,
  details     jsonb,
  created_at  timestamptz not null default now()
);

-- Helpful index for "latest reports for an image"
create index if not exists idx_reports_image_created
  on analysis_reports(image_id, created_at desc);

-- 4) Enable Row Level Security (RLS)
alter table images enable row level security;
alter table analysis_reports enable row level security;

-- 5) RLS Policies
-- Images: only the owner (auth.uid()) may select/insert/update/delete
drop policy if exists images_owner_crud on images;
create policy images_owner_crud
  on images
  for all
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

-- Reports: accessible only if the linked image is owned by the caller
drop policy if exists reports_owner_crud on analysis_reports;
create policy reports_owner_crud
  on analysis_reports
  for all
  using (exists (
    select 1
    from images i
    where i.id = analysis_reports.image_id
      and i.user_id = auth.uid()
  ))
  with check (exists (
    select 1
    from images i
    where i.id = analysis_reports.image_id
      and i.user_id = auth.uid()
  ));
