create table if not exists users (
  id uuid primary key default gen_random_uuid(),
  email text not null unique,
  password_hash text not null,
  created_at timestamptz not null default now()
);

create table if not exists images (
  id uuid primary key default gen_random_uuid(),
  owner_user_id uuid not null references users(id) on delete cascade,
  filename text not null,
  storage_path text,
  labels text[],
  note text,
  uploaded_at timestamptz not null default now()
);

create table if not exists analysis_reports (
  id uuid primary key default gen_random_uuid(),
  image_id uuid not null references images(id) on delete cascade,
  status text not null default 'PENDING',
  confidence double precision,
  details jsonb,
  created_at timestamptz not null default now()
);
