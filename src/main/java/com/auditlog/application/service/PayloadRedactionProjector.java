package com.auditlog.application.service;
import com.fasterxml.jackson.core.JsonPointer; import com.fasterxml.jackson.databind.JsonNode; import com.fasterxml.jackson.databind.node.*; import org.springframework.stereotype.Component; import java.util.List;
@Component public class PayloadRedactionProjector {
 public JsonNode project(JsonNode payload,List<String> pointers){ JsonNode copy=payload.deepCopy(); for(String pointer:pointers) redact(copy,JsonPointer.compile(pointer)); return copy; }
 private void redact(JsonNode root,JsonPointer pointer){ if(pointer.matches()||!pointer.tail().mayMatch()) return; JsonPointer parent=pointer.head(); JsonNode node=root.at(parent); String token=pointer.last().getMatchingProperty(); if(node instanceof ObjectNode object && token!=null) object.put(token,"[REDACTED]"); else if(node instanceof ArrayNode array && pointer.last().mayMatchElement()) array.set(pointer.last().getMatchingIndex(),TextNode.valueOf("[REDACTED]")); }
}
