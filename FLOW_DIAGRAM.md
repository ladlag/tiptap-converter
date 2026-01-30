# 转换流程图 (Conversion Flow)

## 总体架构

```
DocumentData对象 (外部JAR)
         ↓
  TiptapConverter
    (反射访问)
         ↓
   TipTap JSON
```

## 详细转换流程

```
┌─────────────────────────────────────────────────────────────┐
│                      DocumentData                           │
│  - sourceName, title, properties                           │
│  - blocks: List<ContentBlock>                              │
│  - images, attachments, hyperlinks                         │
│  - comments, footnotes, mapped                             │
└─────────────────────────────────────────────────────────────┘
                         ↓
                         ↓ convert()
                         ↓
┌─────────────────────────────────────────────────────────────┐
│              TiptapConverter Processing                     │
│                                                             │
│  1. buildDocAttrs() → 提取元信息                             │
│     - 所有非blocks字段进入doc.attrs                          │
│                                                             │
│  2. convertBlocks() → 转换内容块                            │
│     ├─ 识别Block类型（反射获取类名）                         │
│     ├─ 列表合并（连续列表项）                                │
│     └─ 调用专门转换方法                                      │
│                                                             │
│  3. 专门转换方法:                                            │
│     ├─ convertHeadingBlock()                                │
│     ├─ convertParagraphBlock()                              │
│     ├─ convertList()                                        │
│     ├─ convertTableBlock()                                  │
│     ├─ convertImageBlock()                                  │
│     └─ convertAttachmentBlock()                             │
│                                                             │
│  4. convertRuns() → 转换TextRun                             │
│     ├─ 处理换行符（\n → hardBreak）                         │
│     ├─ 创建text节点                                         │
│     └─ 添加marks（bold/italic/textStyle）                   │
└─────────────────────────────────────────────────────────────┘
                         ↓
                         ↓
                         ↓
┌─────────────────────────────────────────────────────────────┐
│                    TipTap JSON                              │
│  {                                                          │
│    "type": "doc",                                           │
│    "attrs": {                                               │
│      "sourceName": "...",                                   │
│      "title": "...",                                        │
│      "properties": {...},                                   │
│      "images": [...],                                       │
│      "attachments": [...],                                  │
│      "hyperlinks": [...]                                    │
│    },                                                       │
│    "content": [                                             │
│      { "type": "heading", ... },                            │
│      { "type": "paragraph", ... },                          │
│      { "type": "orderedList", ... },                        │
│      { "type": "table", ... },                              │
│      { "type": "image", ... }                               │
│    ]                                                        │
│  }                                                          │
└─────────────────────────────────────────────────────────────┘
```

## Block类型转换矩阵

```
输入Block类型          →  输出TipTap节点
─────────────────────────────────────────────────────────
HeadingBlock           →  heading
                          - level: max(1, min(6, level-1))
                          - originLevel: 原始level
                          - content: [text]

ParagraphBlock         →  paragraph
(isListItem=false)        - attrs.textAlign
                          - attrs.origin: 完整block
                          - content: [text nodes]

ParagraphBlock         →  orderedList / bulletList
(isListItem=true)         - 自动合并连续项
连续多个                   - attrs.listLevel
                          - content: [listItem...]

TableBlock             →  table
                          - content: [tableRow...]
                            - content: [tableCell...]
                              - content: [paragraph]

ImageBlock             →  image
                          - attrs.src, alt, title
                          - attrs.origin: 完整block

AttachmentBlock        →  attachment
                          - attrs.name, url
                          - attrs.origin: 完整block

Unknown Block          →  paragraph
                          - attrs.origin: 完整block
                          - 保证不丢失数据
```

## TextRun转换细节

```
TextRun对象                     →  text节点
─────────────────────────────────────────────────────
{                                  {
  text: "Hello",                     "type": "text",
  bold: true,                        "text": "Hello",
  italic: true,                      "marks": [
  fontFamily: "Arial",                 {"type": "bold"},
  fontSize: 14,                        {"type": "italic"},
  color: "#FF0000",                    {
  backgroundColor: "#FFFF00"             "type": "textStyle",
}                                        "attrs": {
                                           "fontFamily": "Arial",
                                           "fontSize": "14",
                                           "originFontSize": 14,
                                           "color": "#FF0000",
                                           "backgroundColor": "#FFFF00"
                                         }
                                       }
                                     ]
                                   }
```

## 列表合并示例

```
输入Blocks序列:
┌──────────────────────────────┐
│ ParagraphBlock               │
│   isListItem: true           │
│   listType: "ordered"        │
│   listLevel: 1               │
│   runs: ["Item 1"]           │
├──────────────────────────────┤
│ ParagraphBlock               │
│   isListItem: true           │
│   listType: "ordered"        │
│   listLevel: 1               │
│   runs: ["Item 2"]           │
├──────────────────────────────┤
│ ParagraphBlock               │
│   isListItem: true           │
│   listType: "ordered"        │
│   listLevel: 1               │
│   runs: ["Item 3"]           │
└──────────────────────────────┘
         ↓  合并
         ↓
输出TipTap节点:
┌──────────────────────────────┐
│ orderedList                  │
│   attrs: {                   │
│     listLevel: 1,            │
│     numberingFormat: "..."   │
│   }                          │
│   content: [                 │
│     {type: "listItem", ...}, │
│     {type: "listItem", ...}, │
│     {type: "listItem", ...}  │
│   ]                          │
└──────────────────────────────┘
```

## 换行符处理

```
输入: TextRun("Line1\nLine2\nLine3")

          ↓  split by \n
          ↓

输出: [
  {type: "text", text: "Line1"},
  {type: "hardBreak"},
  {type: "text", text: "Line2"},
  {type: "hardBreak"},
  {type: "text", text: "Line3"}
]
```

## 反射机制说明

```java
// 不依赖具体类，通过反射动态访问
private <T> T getFieldValue(Object obj, String methodName) {
    Method method = obj.getClass().getMethod(methodName);
    return (T) method.invoke(obj);
}

// 使用示例
Object block = ...;  // 来自外部JAR
String blockType = block.getClass().getSimpleName();  // "HeadingBlock"
Integer level = getFieldValue(block, "getLevel");     // 调用getLevel()
String text = getFieldValue(block, "getText");        // 调用getText()
```

## 数据流向

```
┌──────────┐     ┌──────────┐     ┌──────────┐
│ External │────→│ TipTap   │────→│ TipTap   │
│   JAR    │     │Converter │     │   JSON   │
│          │     │          │     │          │
│Document  │     │Reflection│     │Frontend  │
│  Data    │     │  Based   │     │ Editor   │
└──────────┘     └──────────┘     └──────────┘
    ↑                                   ↓
    │                                   │
    └────────── Round Trip ─────────────┘
           (无损往返转换)
```

## 关键设计原则

1. **无损性** - 所有数据必须保留
   - TipTap支持的 → 标准结构
   - TipTap不支持的 → attrs.origin

2. **独立性** - TextRun不合并
   - 每个Run独立转换
   - 保持原始分段

3. **智能性** - 列表自动合并
   - 识别连续列表项
   - 保持结构清晰

4. **扩展性** - 支持未知类型
   - 未知Block → paragraph包装
   - 完整数据进origin

5. **兼容性** - 反射动态访问
   - 不依赖编译时类型
   - 支持不同JAR版本
