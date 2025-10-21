package dev.coms4156.project.metadetect.c2pa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Iterator;
import java.util.Map;

/** Parses C2PA manifest JSON into structured data. */

public final class C2paJsonParser {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private C2paJsonParser() {}

    public static Map<String, Object> parseManifestJson(String jsonString) throws Exception {
        try {
            JsonNode root = objectMapper.readTree(jsonString);

            JsonNode manifests = root.get("manifests");
            if (manifests != null && manifests.isArray() && manifests.size() > 0) {
                JsonNode firstManifest = manifests.get(0);
                return parseJsonNode(firstManifest);
            } else {
                throw new Exception("No manifests found in C2PA JSON");
         }
        } catch (Exception ignore) {
            throw new Exception("Failed to parse C2PA JSON", ignore);
        }

    }

    private static Map<String, Object> parseJsonNode(JsonNode node) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();

        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String key = entry.getKey();
            JsonNode valueNode = entry.getValue();

            Object value;
            if (valueNode.isObject()) {
                value = parseJsonNode(valueNode);
            } else if (valueNode.isArray()) {
                java.util.List<Object> list = new java.util.ArrayList<>();
                for (JsonNode arrayItem : valueNode) {
                    if (arrayItem.isObject()) {
                        list.add(parseJsonNode(arrayItem));
                    } else if (arrayItem.isArray()) {
                        // Nested arrays are not expected in C2PA manifests; handle as needed
                        list.add(arrayItem.toString());
                    } else if (arrayItem.isValueNode()) {
                        list.add(arrayItem.asText());
                    }
                }
                value = list;
            } else if (valueNode.isValueNode()) {
                value = valueNode.asText();
            } else {
                value = null; // or handle other types as needed
            }

            result.put(key, value);
        }
        
        return result;
    }
}
