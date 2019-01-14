package com.synopsys.metrics;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class Utils {

  private static ObjectMapper sMapper = null;

  private static ObjectMapper getObjectMapper() {
    if (sMapper == null) {
      sMapper = new ObjectMapper();
      sMapper.configure(JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER, true);
      sMapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
    }
    return sMapper;
  }

  public static String getJsonElement (JsonNode node, String defaultValue) {
    if (node != null) {
      try {
        return getObjectMapper().writeValueAsString(node);
      } catch (JsonProcessingException e) {
        e.printStackTrace();
      }
    }
    return defaultValue;
  }

  public static List<String> getFieldAsStrArray(JsonNode node, String tag, String defaultValue, Consumer<String> setter) {
    ArrayList<String> result = new ArrayList<>();
    if ((node != null) && node.hasNonNull(tag) && node.get(tag).isArray()) {
      for (JsonNode itemNode : node.get(tag)) {
        String value = itemNode.asText(defaultValue);
        result.add(value);
        if (setter != null) {
          setter.accept(value);
        }
      }
    }
    return result;
  }

  public static double getFieldAsDouble(JsonNode node, String tag, double defaultValue, Consumer<Double> setter) {
    double result = defaultValue;
    if ((node != null) && node.hasNonNull(tag) && node.get(tag).isNumber()) {
      result = node.get(tag).asDouble(defaultValue);
      if (setter != null)
        setter.accept(result);
    }
    return result;
  }

  public static String getFieldAsText(JsonNode node, String tag, String defaultValue, Consumer<String> setter) {
    String result = defaultValue;
    if ((node != null) && node.hasNonNull(tag)) {
      result = node.get(tag).asText(defaultValue);
      if (setter != null)
        setter.accept(result);
    }
    return result;
  }

  public static int getFieldAsInt(JsonNode node, String tag, int defaultValue, Consumer<Integer> setter) {
    int result = defaultValue;
    if ((node != null) && node.hasNonNull(tag) && node.get(tag).isInt()) {
      result = node.get(tag).asInt(defaultValue);
      if (setter != null)
        setter.accept(result);
    }
    return result;
  }

  static public JsonNode getJsonNodeFromFile(String pathname) {
    try {
      File file = new File(pathname);
      return getObjectMapper().readTree(file);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  static public JsonNode getJsonNodeFromText(String text) {
    try {
      return getObjectMapper().readTree(text);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }


}
