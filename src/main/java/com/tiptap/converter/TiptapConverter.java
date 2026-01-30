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
     * Enhanced: Detects heading style from paragraph style field
     */
    private Map<String, Object> convertParagraphBlock(Object block) {
        // Check if paragraph has a heading style (legacy Word style detection)
        String paragraphStyle = getFieldValue(block, "getStyle");
        Integer styleLevel = detectHeadingFromStyle(paragraphStyle);
        
        Map<String, Object> node = new LinkedHashMap<>();
        
        // If style indicates heading, create heading node instead
        if (styleLevel != null && styleLevel > 0) {
            node.put("type", "heading");
            Map<String, Object> attrs = new LinkedHashMap<>();
            int tiptapLevel = Math.max(1, Math.min(6, styleLevel));
            attrs.put("level", tiptapLevel);
            attrs.put("originLevel", styleLevel);
            if (paragraphStyle != null) {
                attrs.put("styleSource", paragraphStyle);
            }
            node.put("attrs", attrs);
        } else {
            node.put("type", "paragraph");
            Map<String, Object> attrs = new LinkedHashMap<>();
            
            // Text alignment
            Object alignment = getFieldValue(block, "getAlignment");
            if (alignment != null) {
                String alignValue = getFieldValue(alignment, "getValue");
                if (alignValue != null) {
                    attrs.put("textAlign", alignValue);
                } else {
                    attrs.put("textAlign", alignment.toString().toLowerCase());
                }
            }
            
            // Enhanced: Add indentation, spacing, borders if present
            addParagraphFormatting(attrs, block);
            
            // Preserve original block data
            attrs.put("origin", convertToMap(block));
            node.put("attrs", attrs);
        }
        
        // Convert runs to text nodes
        List<?> runs = getFieldValue(block, "getRuns");
        List<Map<String, Object>> content = convertRuns(runs);
        node.put("content", content);
        
        return node;
    }
    
    /**
     * Detect heading level from paragraph style name (legacy Word style detection)
     * Supports styles like "Heading 1", "Heading1", "标题 1", "Title", etc.
     */
    private Integer detectHeadingFromStyle(String styleName) {
        if (styleName == null || styleName.isEmpty()) {
            return null;
        }
        
        String lowerStyle = styleName.toLowerCase().trim();
        
        // Common heading patterns
        if (lowerStyle.matches(".*heading\\s*[1-6].*") || 
            lowerStyle.matches(".*标题\\s*[1-6].*")) {
            // Extract digit
            for (char c : lowerStyle.toCharArray()) {
                if (Character.isDigit(c)) {
                    int level = Character.getNumericValue(c);
                    if (level >= 1 && level <= 6) {
                        return level;
                    }
                }
            }
        }
        
        // Special cases
        if (lowerStyle.contains("title") || lowerStyle.equals("标题")) {
            return 1;
        }
        if (lowerStyle.contains("subtitle") || lowerStyle.contains("副标题")) {
            return 2;
        }
        
        return null;
    }
    
    /**
     * Add enhanced paragraph formatting attributes (indentation, spacing, borders)
     */
    private void addParagraphFormatting(Map<String, Object> attrs, Object block) {
        // Indentation
        Object indentLeft = getFieldValue(block, "getIndentLeft");
        if (indentLeft != null) {
            attrs.put("indentLeft", indentLeft);
        }
        
        Object indentRight = getFieldValue(block, "getIndentRight");
        if (indentRight != null) {
            attrs.put("indentRight", indentRight);
        }
        
        Object indentFirstLine = getFieldValue(block, "getIndentFirstLine");
        if (indentFirstLine != null) {
            attrs.put("indentFirstLine", indentFirstLine);
        }
        
        // Spacing
        Object spacingBefore = getFieldValue(block, "getSpacingBefore");
        if (spacingBefore != null) {
            attrs.put("spacingBefore", spacingBefore);
        }
        
        Object spacingAfter = getFieldValue(block, "getSpacingAfter");
        if (spacingAfter != null) {
            attrs.put("spacingAfter", spacingAfter);
        }
        
        Object lineSpacing = getFieldValue(block, "getLineSpacing");
        if (lineSpacing != null) {
            attrs.put("lineSpacing", lineSpacing);
        }
        
        // Borders
        Object borderTop = getFieldValue(block, "getBorderTop");
        if (borderTop != null) {
            attrs.put("borderTop", convertToMap(borderTop));
        }
        
        Object borderBottom = getFieldValue(block, "getBorderBottom");
        if (borderBottom != null) {
            attrs.put("borderBottom", convertToMap(borderBottom));
        }
        
        Object borderLeft = getFieldValue(block, "getBorderLeft");
        if (borderLeft != null) {
            attrs.put("borderLeft", convertToMap(borderLeft));
        }
        
        Object borderRight = getFieldValue(block, "getBorderRight");
        if (borderRight != null) {
            attrs.put("borderRight", convertToMap(borderRight));
        }
        
        // Background color
        Object backgroundColor = getFieldValue(block, "getBackgroundColor");
        if (backgroundColor != null) {
            attrs.put("backgroundColor", backgroundColor);
        }
    }

    /**
     * Convert list of TextRuns to text nodes with marks
     * Each run becomes a separate text node (no merging)
     * Handles \n by splitting into text + hardBreak + text
     * Enhanced: Handles embedded images and attachments within runs
     */
    private List<Map<String, Object>> convertRuns(List<?> runs) {
        List<Map<String, Object>> content = new ArrayList<>();
        
        if (runs == null || runs.isEmpty()) {
            return content;
        }
        
        for (Object run : runs) {
            // Enhanced: Check for embedded images in run
            List<?> embeddedImages = getFieldValue(run, "getEmbeddedImages");
            if (embeddedImages != null && !embeddedImages.isEmpty()) {
                for (Object img : embeddedImages) {
                    content.add(convertEmbeddedImage(img));
                }
            }
            
            // Enhanced: Check for embedded attachments in run
            List<?> embeddedAttachments = getFieldValue(run, "getEmbeddedAttachments");
            if (embeddedAttachments != null && !embeddedAttachments.isEmpty()) {
                for (Object attachment : embeddedAttachments) {
                    content.add(convertEmbeddedAttachment(attachment));
                }
            }
            
            // Process text content
            String text = getFieldValue(run, "getText");
            if (text == null || text.isEmpty()) {
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
     * Convert embedded image from run to image node
     * This handles images that appear inline within text runs (legacy setPictures logic)
     */
    private Map<String, Object> convertEmbeddedImage(Object imageData) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", "image");
        
        Map<String, Object> attrs = new LinkedHashMap<>();
        
        String src = getFieldValue(imageData, "getSrc");
        if (src == null) {
            src = getFieldValue(imageData, "getUrl");
        }
        if (src == null) {
            src = getFieldValue(imageData, "getPath");
        }
        if (src != null) {
            attrs.put("src", src);
        }
        
        String alt = getFieldValue(imageData, "getAlt");
        if (alt != null) {
            attrs.put("alt", alt);
        }
        
        String title = getFieldValue(imageData, "getTitle");
        if (title != null) {
            attrs.put("title", title);
        }
        
        Integer width = getFieldValue(imageData, "getWidth");
        if (width != null) {
            attrs.put("width", width);
        }
        
        Integer height = getFieldValue(imageData, "getHeight");
        if (height != null) {
            attrs.put("height", height);
        }
        
        // Preserve full data
        attrs.put("origin", convertToMap(imageData));
        node.put("attrs", attrs);
        
        return node;
    }
    
    /**
     * Convert embedded attachment from run to attachment node
     * This handles attachments that appear inline within text runs (legacy setFile logic)
     */
    private Map<String, Object> convertEmbeddedAttachment(Object attachmentData) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", "attachment");
        
        Map<String, Object> attrs = new LinkedHashMap<>();
        
        String name = getFieldValue(attachmentData, "getName");
        if (name == null) {
            name = getFieldValue(attachmentData, "getFileName");
        }
        if (name != null) {
            attrs.put("name", name);
        }
        
        String url = getFieldValue(attachmentData, "getUrl");
        if (url == null) {
            url = getFieldValue(attachmentData, "getPath");
        }
        if (url != null) {
            attrs.put("url", url);
        }
        
        Long size = getFieldValue(attachmentData, "getSize");
        if (size != null) {
            attrs.put("size", size);
        }
        
        String mimeType = getFieldValue(attachmentData, "getMimeType");
        if (mimeType == null) {
            mimeType = getFieldValue(attachmentData, "getContentType");
        }
        if (mimeType != null) {
            attrs.put("mimeType", mimeType);
        }
        
        // Preserve full data
        attrs.put("origin", convertToMap(attachmentData));
        node.put("attrs", attrs);
        
        return node;
    }

    /**
     * Create a text node with marks from a TextRun
     * Enhanced: Supports strikethrough, subscript, superscript, and more
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
        
        // Enhanced: Strikethrough support
        Boolean strikethrough = getFieldValue(run, "getStrikethrough");
        if (Boolean.TRUE.equals(strikethrough)) {
            Map<String, Object> mark = new LinkedHashMap<>();
            mark.put("type", "strike");
            marks.add(mark);
        }
        
        // Enhanced: Subscript/Superscript support
        String verticalAlign = getFieldValue(run, "getVerticalAlign");
        if ("subscript".equalsIgnoreCase(verticalAlign)) {
            Map<String, Object> mark = new LinkedHashMap<>();
            mark.put("type", "subscript");
            marks.add(mark);
        } else if ("superscript".equalsIgnoreCase(verticalAlign)) {
            Map<String, Object> mark = new LinkedHashMap<>();
            mark.put("type", "superscript");
            marks.add(mark);
        }
        
        // Enhanced: Code/Monospace support
        Boolean code = getFieldValue(run, "getCode");
        if (Boolean.TRUE.equals(code)) {
            Map<String, Object> mark = new LinkedHashMap<>();
            mark.put("type", "code");
            marks.add(mark);
        }
        
        // Enhanced: Highlight support
        String highlight = getFieldValue(run, "getHighlight");
        if (highlight != null && !highlight.isEmpty()) {
            Map<String, Object> mark = new LinkedHashMap<>();
            mark.put("type", "highlight");
            Map<String, Object> highlightAttrs = new LinkedHashMap<>();
            highlightAttrs.put("color", highlight);
            mark.put("attrs", highlightAttrs);
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
        
        // Enhanced: Link support (hyperlinks within runs)
        String hyperlinkId = getFieldValue(run, "getHyperlinkId");
        String hyperlinkUrl = getFieldValue(run, "getHyperlinkUrl");
        if (hyperlinkUrl != null || hyperlinkId != null) {
            Map<String, Object> linkMark = new LinkedHashMap<>();
            linkMark.put("type", "link");
            Map<String, Object> linkAttrs = new LinkedHashMap<>();
            if (hyperlinkUrl != null) {
                linkAttrs.put("href", hyperlinkUrl);
            }
            if (hyperlinkId != null) {
                linkAttrs.put("linkId", hyperlinkId);
            }
            String linkTarget = getFieldValue(run, "getLinkTarget");
            if (linkTarget != null) {
                linkAttrs.put("target", linkTarget);
            }
            linkMark.put("attrs", linkAttrs);
            marks.add(linkMark);
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
     * Enhanced: Supports cell attributes (colspan, rowspan, borders, background)
     */
    private Map<String, Object> convertTableBlock(Object block) {
        Map<String, Object> tableNode = new LinkedHashMap<>();
        tableNode.put("type", "table");
        
        Map<String, Object> attrs = new LinkedHashMap<>();
        
        // Enhanced: Add table-level attributes
        Integer tableWidth = getFieldValue(block, "getWidth");
        if (tableWidth != null) {
            attrs.put("tableWidth", tableWidth);
        }
        
        String tableBorder = getFieldValue(block, "getBorder");
        if (tableBorder != null) {
            attrs.put("tableBorder", tableBorder);
        }
        
        String tableAlignment = getFieldValue(block, "getAlignment");
        if (tableAlignment != null) {
            attrs.put("tableAlignment", tableAlignment);
        }
        
        attrs.put("origin", convertToMap(block));
        tableNode.put("attrs", attrs);
        
        List<Map<String, Object>> content = new ArrayList<>();
        List<?> rows = getFieldValue(block, "getRows");
        
        if (rows != null) {
            for (Object rowObj : rows) {
                Map<String, Object> rowNode = new LinkedHashMap<>();
                rowNode.put("type", "tableRow");
                
                // Enhanced: Add row attributes
                Map<String, Object> rowAttrs = new LinkedHashMap<>();
                Integer rowHeight = getFieldValue(rowObj, "getHeight");
                if (rowHeight != null) {
                    rowAttrs.put("height", rowHeight);
                }
                if (!rowAttrs.isEmpty()) {
                    rowNode.put("attrs", rowAttrs);
                }
                
                List<Map<String, Object>> cells = new ArrayList<>();
                if (rowObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<?> row = (List<?>) rowObj;
                    for (Object cell : row) {
                        Map<String, Object> cellNode = new LinkedHashMap<>();
                        cellNode.put("type", "tableCell");
                        
                        // Enhanced: Add cell attributes (colspan, rowspan, borders, background)
                        Map<String, Object> cellAttrs = new LinkedHashMap<>();
                        
                        Integer colspan = getFieldValue(cell, "getColspan");
                        if (colspan != null && colspan > 1) {
                            cellAttrs.put("colspan", colspan);
                        }
                        
                        Integer rowspan = getFieldValue(cell, "getRowspan");
                        if (rowspan != null && rowspan > 1) {
                            cellAttrs.put("rowspan", rowspan);
                        }
                        
                        Integer cellWidth = getFieldValue(cell, "getWidth");
                        if (cellWidth != null) {
                            cellAttrs.put("colwidth", new int[]{cellWidth});
                        }
                        
                        String cellBackground = getFieldValue(cell, "getBackgroundColor");
                        if (cellBackground != null) {
                            cellAttrs.put("background", cellBackground);
                        }
                        
                        String verticalAlign = getFieldValue(cell, "getVerticalAlign");
                        if (verticalAlign != null) {
                            cellAttrs.put("verticalAlign", verticalAlign);
                        }
                        
                        // Cell borders
                        Object cellBorderTop = getFieldValue(cell, "getBorderTop");
                        if (cellBorderTop != null) {
                            cellAttrs.put("borderTop", convertToMap(cellBorderTop));
                        }
                        
                        Object cellBorderBottom = getFieldValue(cell, "getBorderBottom");
                        if (cellBorderBottom != null) {
                            cellAttrs.put("borderBottom", convertToMap(cellBorderBottom));
                        }
                        
                        Object cellBorderLeft = getFieldValue(cell, "getBorderLeft");
                        if (cellBorderLeft != null) {
                            cellAttrs.put("borderLeft", convertToMap(cellBorderLeft));
                        }
                        
                        Object cellBorderRight = getFieldValue(cell, "getBorderRight");
                        if (cellBorderRight != null) {
                            cellAttrs.put("borderRight", convertToMap(cellBorderRight));
                        }
                        
                        if (!cellAttrs.isEmpty()) {
                            cellNode.put("attrs", cellAttrs);
                        }
                        
                        // Create paragraph content for cell
                        Map<String, Object> paraNode = new LinkedHashMap<>();
                        paraNode.put("type", "paragraph");
                        
                        List<Map<String, Object>> paraContent = new ArrayList<>();
                        
                        // Enhanced: Support rich content in cells (not just plain text)
                        List<?> cellRuns = getFieldValue(cell, "getRuns");
                        if (cellRuns != null && !cellRuns.isEmpty()) {
                            // Cell has formatted runs
                            paraContent = convertRuns(cellRuns);
                        } else {
                            // Fallback to plain text
                            String cellText = getFieldValue(cell, "getText");
                            if (cellText != null && !cellText.isEmpty()) {
                                Map<String, Object> textNode = new LinkedHashMap<>();
                                textNode.put("type", "text");
                                textNode.put("text", cellText);
                                paraContent.add(textNode);
                            }
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
