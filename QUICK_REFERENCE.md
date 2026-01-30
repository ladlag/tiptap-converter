# 快速参考 (Quick Reference)

## 转换器使用（3行代码）

```java
TiptapConverter converter = new TiptapConverter();
Map<String, Object> result = converter.convert(documentData);
String json = new ObjectMapper().writeValueAsString(result);
```

## Block类型映射速查表

| Java类 | TipTap节点 | 关键属性 |
|--------|-----------|----------|
| `HeadingBlock` | `heading` | `level` (1-6), `originLevel` |
| `ParagraphBlock` | `paragraph` | `textAlign`, `origin` |
| `ParagraphBlock` (列表) | `orderedList` / `bulletList` | `listLevel`, `numberingFormat` |
| `TableBlock` | `table` | 嵌套 `tableRow` → `tableCell` → `paragraph` |
| `ImageBlock` | `image` | `src`, `alt`, `title` |
| `AttachmentBlock` | `attachment` | `name`, `url` |

## TextRun样式映射

| 属性 | Mark类型 | 位置 |
|------|---------|------|
| `bold: true` | `bold` | `marks[]` |
| `italic: true` | `italic` | `marks[]` |
| `underline: true` | `underline` | `marks[]` |
| `fontFamily` | `textStyle` | `marks[].attrs.fontFamily` |
| `fontSize` | `textStyle` | `marks[].attrs.fontSize` + `originFontSize` |
| `color` | `textStyle` | `marks[].attrs.color` |
| `backgroundColor` | `textStyle` | `marks[].attrs.backgroundColor` |

## 输出JSON结构

```json
{
  "type": "doc",
  "attrs": {
    "sourceName": "...",
    "title": "...",
    "properties": {...},
    "images": [...],
    "attachments": [...],
    "hyperlinks": [...]
  },
  "content": [
    {
      "type": "heading",
      "attrs": {"level": 1, "originLevel": 2},
      "content": [{"type": "text", "text": "..."}]
    },
    {
      "type": "paragraph",
      "attrs": {"textAlign": "left", "origin": {...}},
      "content": [
        {"type": "text", "text": "Normal "},
        {
          "type": "text",
          "text": "Bold",
          "marks": [
            {"type": "bold"},
            {"type": "textStyle", "attrs": {"fontSize": "14"}}
          ]
        }
      ]
    },
    {
      "type": "orderedList",
      "attrs": {"listLevel": 1},
      "content": [
        {
          "type": "listItem",
          "content": [{"type": "paragraph", ...}]
        }
      ]
    }
  ]
}
```

## 关键特性

### ✅ 无损转换
- 所有字段保留（包括null）
- 不支持的字段放在 `attrs.origin`
- blocks顺序保持一致

### ✅ 自动列表合并
连续的列表项（相同type和level）自动合并：
```
ParagraphBlock(isListItem, ordered, 1)
ParagraphBlock(isListItem, ordered, 1)  →  orderedList { 2 items }
ParagraphBlock(isListItem, ordered, 1)
```

### ✅ TextRun独立性
每个TextRun转换为独立text节点，**不合并**：
```
[Run("Hello "), Run("world")]  →  [text("Hello "), text("world")]
```

### ✅ 换行处理
`\n` 自动拆分为 text + hardBreak：
```
Run("Line1\nLine2")  →  [text("Line1"), hardBreak(), text("Line2")]
```

## 常用代码片段

### 从文件读取并转换
```java
ObjectMapper mapper = new ObjectMapper();
DocumentData doc = mapper.readValue(new File("input.json"), DocumentData.class);
Map<String, Object> result = new TiptapConverter().convert(doc);
mapper.writeValue(new File("output.json"), result);
```

### 批量转换
```java
TiptapConverter converter = new TiptapConverter();
List<Map<String, Object>> results = documents.stream()
    .map(converter::convert)
    .collect(Collectors.toList());
```

### 提取特定信息
```java
Map<String, Object> result = converter.convert(doc);

// 获取标题
String title = (String) ((Map) result.get("attrs")).get("title");

// 获取内容块数量
int blockCount = ((List) result.get("content")).size();

// 获取所有图片
List images = (List) ((Map) result.get("attrs")).get("images");
```

## 依赖要求

- **JDK**: 8+
- **Jackson**: 2.15.2+ (`jackson-databind`)
- **模型JAR**: 包含 `DocumentData` 和 Block类

## Maven坐标

```xml
<dependency>
    <groupId>com.tiptap</groupId>
    <artifactId>tiptap-converter</artifactId>
    <version>1.0.0</version>
</dependency>
```

## 性能参考

- 小文档 (< 100 blocks): < 10ms
- 中等文档 (100-1000 blocks): < 100ms  
- 大文档 (> 1000 blocks): 建议流式处理

## 故障排查

| 错误 | 原因 | 解决 |
|------|------|------|
| `ClassNotFoundException` | 模型JAR不在classpath | 添加JAR到classpath |
| `NoSuchMethodException` | 模型类缺少getter | 为字段添加getter方法 |
| `NullPointerException` | DocumentData为null | 检查输入数据 |
| 输出不完整 | 某些字段没有getter | 检查JavaBean规范 |

## 更多文档

- 📖 **README.md** - 完整功能说明
- 🔧 **INTEGRATION.md** - 集成指南
- 📝 **examples/** - 输入输出示例
