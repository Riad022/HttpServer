package com.riad.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;

public class Json {

    private static ObjectMapper mapper = defaultMapper();

    public static ObjectMapper defaultMapper() {
        ObjectMapper om = new ObjectMapper();
        om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return om;
    }

    public static JsonNode parse(String json) throws JsonProcessingException {
        return mapper.readTree(json);
    }

    public static <A> A fromJson(JsonNode node, Class<A> clazz) throws JsonProcessingException {
        return mapper.treeToValue(node, clazz);
    }

    public static JsonNode toJson(Object obj) {
        return mapper.valueToTree(obj);
    }

    public String stringify(JsonNode node) throws JsonProcessingException {
        return generateJson(node,false);
    }

    public String stringifyPretty(JsonNode node) throws JsonProcessingException {
        return generateJson(node,true);
    }


    private static String generateJson(Object obj, boolean pretty) throws JsonProcessingException {
        ObjectWriter ow = mapper.writer();

        if(pretty){
            ow = ow.with(SerializationFeature.INDENT_OUTPUT);
        }
        return ow.writeValueAsString(obj);
    }
}
