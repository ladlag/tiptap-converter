package com.tiptap.converter;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Main converter class that transforms DocumentData to TipTap/ProseMirror JSON
 * This converter ensures lossless conversion - all fields are preserved
 * 
 * Note: This converter works with DocumentData and Block classes from external JAR.
 * It uses reflection to access fields dynamically.
 */
public class TiptapConverter {

    private final ObjectMapper objectMapper;

    public TiptapConverter() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Main conversion method
     * @param documentData The source document data (from external JAR)
     * @return TipTap/ProseMirror compatible JSON structure as Map
     */
    public Map<String, Object> convert(Object documentData) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("type", "doc");
        
        // Build attrs with all metadata (lossless preservation)
        Map<String, Object> attrs = buildDocAttrs(documentData);
        doc.put("attrs", attrs);
        
        // Convert blocks to content
        List<?> blocks = getFieldValue(documentData, "getBlocks");
        List<Map<String, Object>> content = convertBlocks(blocks);
        doc.put("content", content);
        
        return doc;
    }

    /**
     * Build doc.attrs with all metadata from DocumentData
     * This ensures no information is lost
     */
    private Map<String, Object> buildDocAttrs(Object documentData) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        
        // Preserve all metadata fields using reflection
        addIfNotNull(attrs, "sourceName", getFieldValue(documentData, "getSourceName"));
        addIfNotNull(attrs, "title", getFieldValue(documentData, "getTitle"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = getFieldValue(documentData, "getProperties");
        if (properties != null && !properties.isEmpty()) {
            attrs.put("properties", properties);
        }
        
        List<?> images = getFieldValue(documentData, "getImages");
        if (images != null && !images.isEmpty()) {
            attrs.put("images", convertToMapList(images));
        }
        
        List<?> attachments = getFieldValue(documentData, "getAttachments");
        if (attachments != null && !attachments.isEmpty()) {
            attrs.put("attachments", convertToMapList(attachments));
        }
        
        List<?> hyperlinks = getFieldValue(documentData, "getHyperlinks");
        if (hyperlinks != null && !hyperlinks.isEmpty()) {
            attrs.put("hyperlinks", convertToMapList(hyperlinks));
        }
        
        addIfNotNull(attrs, "comments", getFieldValue(documentData, "getComments"));
        addIfNotNull(attrs, "footnotes", getFieldValue(documentData, "getFootnotes"));
        addIfNotNull(attrs, "mapped", getFieldValue(documentData, "getMapped"));
        
        return attrs;
    }

    /**
     * Convert a list of blocks to TipTap content nodes
     * Handles list merging for consecutive list items
     */
    private List<Map<String, Object>> convertBlocks(List<?> blocks) {
        List<Map<String, Object>> content = new ArrayList<>();
        
        if (blocks == null || blocks.isEmpty()) {
            return content;
        }
        
        int i = 0;
        while (i < blocks.size()) {
            Object block = blocks.get(i);
            String blockType = getBlockType(block);
            
            // Check if this is a list item
            if ("ParagraphBlock".equals(blockType)) {
                Boolean isListItem = getFieldValue(block, "isListItem");
                if (Boolean.TRUE.equals(isListItem)) {
                    // Merge consecutive list items into a list container
                    List<Object> listItems = new ArrayList<>();
                    String listType = getFieldValue(block, "getListType");
                    Integer listLevel = getFieldValue(block, "getListLevel");
                    
                    // Collect consecutive items with same type and level
                    while (i < blocks.size()) {
                        Object current = blocks.get(i);
                        if ("ParagraphBlock".equals(getBlockType(current))) {
                            Boolean currentIsListItem = getFieldValue(current, "isListItem");
                            String currentListType = getFieldValue(current, "getListType");
                            Integer currentListLevel = getFieldValue(current, "getListLevel");
                            
                            if (Boolean.TRUE.equals(currentIsListItem) && 
                                Objects.equals(listType, currentListType) &&
                                Objects.equals(listLevel, currentListLevel)) {
                                listItems.add(current);
                                i++;
                            } else {
                                break;
                            }
                        } else {
                            break;
                        }
                    }
                    
                    // Create list container
                    content.add(convertList(listItems, listType, listLevel, listItems.get(0)));
                } else {
                    // Regular paragraph
                    Map<String, Object> node = convertBlock(block);
                    if (node != null) {
                        content.add(node);
                    }
                    i++;
                }
            } else {
                // Convert individual block
                Map<String, Object> node = convertBlock(block);
                if (node != null) {
                    content.add(node);
                }
                i++;
            }
        }
        
        return content;
    }

    /**
     * Get block type from class name
     */
    private String getBlockType(Object block) {
        if (block == null) {
            return "Unknown";
        }
        String className = block.getClass().getSimpleName();
        return className;
    }

    /**
     * Convert a single block based on its type
     */
    private Map<String, Object> convertBlock(Object block) {
        String blockType = getBlockType(block);
        
        switch (blockType) {
            case "HeadingBlock":
                return convertHeadingBlock(block);
            case "ParagraphBlock":
                return convertParagraphBlock(block);
            case "TableBlock":
                return convertTableBlock(block);
            case "ImageBlock":
                return convertImageBlock(block);
            case "AttachmentBlock":
                return convertAttachmentBlock(block);
            default:
                // Unknown block type - preserve in origin
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("type", "paragraph");
                Map<String, Object> attrs = new LinkedHashMap<>();
                attrs.put("origin", convertToMap(block));
                node.put("attrs", attrs);
                node.put("content", Collections.emptyList());
                return node;
        }
    }

    /**
     * Convert HeadingBlock to heading node
     */
    private Map<String, Object> convertHeadingBlock(Object block) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", "heading");
        
        Integer level = getFieldValue(block, "getLevel");
        String text = getFieldValue(block, "getText");
        
        Map<String, Object> attrs = new LinkedHashMap<>();
        // Map level: tiptapLevel = max(1, min(6, block.level - 1))
        int tiptapLevel = Math.max(1, Math.min(6, (level != null ? level : 1) - 1));
        attrs.put("level", tiptapLevel);
        attrs.put("originLevel", level);
        node.put("attrs", attrs);
        
        // Create text content
        List<Map<String, Object>> content = new ArrayList<>();
        if (text != null && !text.isEmpty()) {
            Map<String, Object> textNode = new LinkedHashMap<>();
            textNode.put("type", "text");
            textNode.put("text", text);
            content.add(textNode);
        }
        node.put("content", content);
        
        return node;
    }

    /**
     * Convert ParagraphBlock to paragraph node
     */
    private Map<String, Object> convertParagraphBlock(Object block) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", "paragraph");
        
        Map<String, Object> attrs = new LinkedHashMap<>();
        Object alignment = getFieldValue(block, "getAlignment");
        if (alignment != null) {
            // Try to get value from enum
            String alignValue = getFieldValue(alignment, "getValue");
            if (alignValue != null) {
                attrs.put("textAlign", alignValue);
            } else {
                attrs.put("textAlign", alignment.toString().toLowerCase());
            }
        }
        // Preserve original block data
        attrs.put("origin", convertToMap(block));
        node.put("attrs", attrs);
        
        // Convert runs to text nodes
        List<?> runs = getFieldValue(block, "getRuns");
        List<Map<String, Object>> content = convertRuns(runs);
        node.put("content", content);
        
        return node;
    }

    /**
     * Convert list of TextRuns to text nodes with marks
     * Each run becomes a separate text node (no merging)
     * Handles \n by splitting into text + hardBreak + text
     */
    private List<Map<String, Object>> convertRuns(List<?> runs) {
        List<Map<String, Object>> content = new ArrayList<>();
        
        if (runs == null || runs.isEmpty()) {
            return content;
        }
        
        for (Object run : runs) {
            String text = getFieldValue(run, "getText");
            if (text == null) {
                continue;
            }
            
            // Handle newlines by splitting
            if (text.contains("\n")) {
                String[] parts = text.split("\n", -1);
                for (int i = 0; i < parts.length; i++) {
                    if (i > 0) {
                        // Add hardBreak between parts
                        Map<String, Object> hardBreak = new LinkedHashMap<>();
                        hardBreak.put("type", "hardBreak");
                        content.add(hardBreak);
                    }
                    if (!parts[i].isEmpty()) {
                        content.add(createTextNode(parts[i], run));
                    }
                }
            } else {
                content.add(createTextNode(text, run));
            }
        }
        
        return content;
    }

    /**
     * Create a text node with marks from a TextRun
     */
    private Map<String, Object> createTextNode(String text, Object run) {
        Map<String, Object> textNode = new LinkedHashMap<>();
        textNode.put("type", "text");
        textNode.put("text", text);
        
        List<Map<String, Object>> marks = new ArrayList<>();
        
        // Add formatting marks
        Boolean bold = getFieldValue(run, "getBold");
        if (Boolean.TRUE.equals(bold)) {
            Map<String, Object> mark = new LinkedHashMap<>();
            mark.put("type", "bold");
            marks.add(mark);
        }
        
        Boolean italic = getFieldValue(run, "getItalic");
        if (Boolean.TRUE.equals(italic)) {
            Map<String, Object> mark = new LinkedHashMap<>();
            mark.put("type", "italic");
            marks.add(mark);
        }
        
        Boolean underline = getFieldValue(run, "getUnderline");
        if (Boolean.TRUE.equals(underline)) {
            Map<String, Object> mark = new LinkedHashMap<>();
            mark.put("type", "underline");
            marks.add(mark);
        }
        
        // Add textStyle mark for font/color attributes
        Map<String, Object> textStyleAttrs = new LinkedHashMap<>();
        boolean hasTextStyle = false;
        
        String fontFamily = getFieldValue(run, "getFontFamily");
        if (fontFamily != null) {
            textStyleAttrs.put("fontFamily", fontFamily);
            hasTextStyle = true;
        }
        
        Object fontSize = getFieldValue(run, "getFontSize");
        if (fontSize != null) {
            // Store both string and original value
            textStyleAttrs.put("fontSize", fontSize.toString());
            if (fontSize instanceof Number) {
                textStyleAttrs.put("originFontSize", ((Number) fontSize).intValue());
            } else {
                textStyleAttrs.put("originFontSize", fontSize);
            }
            hasTextStyle = true;
        }
        
        String color = getFieldValue(run, "getColor");
        if (color != null) {
            textStyleAttrs.put("color", color);
            hasTextStyle = true;
        }
        
        String backgroundColor = getFieldValue(run, "getBackgroundColor");
        if (backgroundColor != null) {
            textStyleAttrs.put("backgroundColor", backgroundColor);
            hasTextStyle = true;
        }
        
        if (hasTextStyle) {
            Map<String, Object> textStyleMark = new LinkedHashMap<>();
            textStyleMark.put("type", "textStyle");
            textStyleMark.put("attrs", textStyleAttrs);
            marks.add(textStyleMark);
        }
        
        if (!marks.isEmpty()) {
            textNode.put("marks", marks);
        }
        
        return textNode;
    }

    /**
     * Convert list of paragraph blocks to a list container (orderedList or bulletList)
     */
    private Map<String, Object> convertList(List<Object> listItems, String listType, 
                                           Integer listLevel, Object firstItem) {
        Map<String, Object> listNode = new LinkedHashMap<>();
        
        // Determine list type
        String nodeType = "ordered".equals(listType) ? "orderedList" : "bulletList";
        listNode.put("type", nodeType);
        
        Map<String, Object> attrs = new LinkedHashMap<>();
        if (listLevel != null) {
            attrs.put("listLevel", listLevel);
        }
        
        String numberingFormat = getFieldValue(firstItem, "getNumberingFormat");
        if (numberingFormat != null) {
            attrs.put("numberingFormat", numberingFormat);
        }
        // Preserve original data
        attrs.put("origin", convertToMap(firstItem));
        listNode.put("attrs", attrs);
        
        // Create list items
        List<Map<String, Object>> content = new ArrayList<>();
        for (Object para : listItems) {
            Map<String, Object> listItemNode = new LinkedHashMap<>();
            listItemNode.put("type", "listItem");
            
            // Each list item contains a paragraph
            List<Map<String, Object>> itemContent = new ArrayList<>();
            Map<String, Object> paragraphNode = new LinkedHashMap<>();
            paragraphNode.put("type", "paragraph");
            
            Map<String, Object> paraAttrs = new LinkedHashMap<>();
            Object alignment = getFieldValue(para, "getAlignment");
            if (alignment != null) {
                String alignValue = getFieldValue(alignment, "getValue");
                if (alignValue != null) {
                    paraAttrs.put("textAlign", alignValue);
                } else {
                    paraAttrs.put("textAlign", alignment.toString().toLowerCase());
                }
            }
            paragraphNode.put("attrs", paraAttrs);
            
            List<?> runs = getFieldValue(para, "getRuns");
            paragraphNode.put("content", convertRuns(runs));
            itemContent.add(paragraphNode);
            
            listItemNode.put("content", itemContent);
            content.add(listItemNode);
        }
        
        listNode.put("content", content);
        return listNode;
    }

    /**
     * Convert TableBlock to table node
     */
    private Map<String, Object> convertTableBlock(Object block) {
        Map<String, Object> tableNode = new LinkedHashMap<>();
        tableNode.put("type", "table");
        
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("origin", convertToMap(block));
        tableNode.put("attrs", attrs);
        
        List<Map<String, Object>> content = new ArrayList<>();
        List<?> rows = getFieldValue(block, "getRows");
        
        if (rows != null) {
            for (Object rowObj : rows) {
                Map<String, Object> rowNode = new LinkedHashMap<>();
                rowNode.put("type", "tableRow");
                
                List<Map<String, Object>> cells = new ArrayList<>();
                if (rowObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<?> row = (List<?>) rowObj;
                    for (Object cell : row) {
                        Map<String, Object> cellNode = new LinkedHashMap<>();
                        cellNode.put("type", "tableCell");
                        
                        // Create paragraph content for cell
                        Map<String, Object> paraNode = new LinkedHashMap<>();
                        paraNode.put("type", "paragraph");
                        
                        List<Map<String, Object>> paraContent = new ArrayList<>();
                        String cellText = getFieldValue(cell, "getText");
                        if (cellText != null && !cellText.isEmpty()) {
                            Map<String, Object> textNode = new LinkedHashMap<>();
                            textNode.put("type", "text");
                            textNode.put("text", cellText);
                            paraContent.add(textNode);
                        }
                        paraNode.put("content", paraContent);
                        
                        cellNode.put("content", Collections.singletonList(paraNode));
                        cells.add(cellNode);
                    }
                }
                
                rowNode.put("content", cells);
                content.add(rowNode);
            }
        }
        
        tableNode.put("content", content);
        return tableNode;
    }

    /**
     * Convert ImageBlock to image node
     */
    private Map<String, Object> convertImageBlock(Object block) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", "image");
        
        Map<String, Object> attrs = new LinkedHashMap<>();
        
        String src = getFieldValue(block, "getSrc");
        if (src != null) {
            attrs.put("src", src);
        }
        
        String alt = getFieldValue(block, "getAlt");
        if (alt != null) {
            attrs.put("alt", alt);
        }
        
        String title = getFieldValue(block, "getTitle");
        if (title != null) {
            attrs.put("title", title);
        }
        
        // Preserve full block data
        attrs.put("origin", convertToMap(block));
        node.put("attrs", attrs);
        
        return node;
    }

    /**
     * Convert AttachmentBlock to attachment node
     */
    private Map<String, Object> convertAttachmentBlock(Object block) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", "attachment");
        
        Map<String, Object> attrs = new LinkedHashMap<>();
        
        String name = getFieldValue(block, "getName");
        if (name != null) {
            attrs.put("name", name);
        }
        
        String url = getFieldValue(block, "getUrl");
        if (url != null) {
            attrs.put("url", url);
        }
        
        // Preserve full block data
        attrs.put("origin", convertToMap(block));
        node.put("attrs", attrs);
        
        return node;
    }

    /**
     * Helper method to get field value using reflection
     */
    @SuppressWarnings("unchecked")
    private <T> T getFieldValue(Object obj, String methodName) {
        if (obj == null) {
            return null;
        }
        try {
            Method method = obj.getClass().getMethod(methodName);
            return (T) method.invoke(obj);
        } catch (Exception e) {
            // Try as field name if method doesn't exist
            try {
                String fieldMethodName = methodName.startsWith("get") || methodName.startsWith("is") 
                    ? methodName 
                    : "get" + methodName.substring(0, 1).toUpperCase() + methodName.substring(1);
                Method method = obj.getClass().getMethod(fieldMethodName);
                return (T) method.invoke(obj);
            } catch (Exception ex) {
                // Method doesn't exist, return null
                return null;
            }
        }
    }

    /**
     * Helper method to add value to map if not null
     */
    private void addIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    /**
     * Convert an object to Map using Jackson
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> convertToMap(Object obj) {
        return objectMapper.convertValue(obj, Map.class);
    }

    /**
     * Convert a list of objects to list of maps
     */
    private List<Map<String, Object>> convertToMapList(List<?> list) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            result.add(convertToMap(item));
        }
        return result;
    }
}
