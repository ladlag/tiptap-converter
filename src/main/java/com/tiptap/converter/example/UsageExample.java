package com.tiptap.converter.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiptap.converter.TiptapConverter;

import java.util.Map;

/**
 * 使用示例
 * 
 * 注意：此类需要在classpath中包含DocumentData和Block类的JAR才能运行
 * 
 * 运行方式：
 * java -cp your-document-model.jar:tiptap-converter.jar:jackson-databind.jar \
 *      com.tiptap.converter.example.UsageExample
 */
public class UsageExample {
    
    public static void main(String[] args) throws Exception {
        // 假设您已经有了DocumentData对象
        // DocumentData doc = loadYourDocument();
        
        System.out.println("=== TipTap Converter 使用示例 ===\n");
        
        // 示例代码（需要实际的DocumentData实例）
        printUsageInstructions();
    }
    
    /**
     * 转换DocumentData到TipTap JSON的基本流程
     */
    public static Map<String, Object> convertDocument(Object documentData) throws Exception {
        // 1. 创建转换器实例
        TiptapConverter converter = new TiptapConverter();
        
        // 2. 执行转换
        Map<String, Object> tiptapDocument = converter.convert(documentData);
        
        // 3. 可选：输出为JSON字符串
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writerWithDefaultPrettyPrinter()
                           .writeValueAsString(tiptapDocument);
        System.out.println(json);
        
        return tiptapDocument;
    }
    
    /**
     * 打印使用说明
     */
    private static void printUsageInstructions() {
        System.out.println("使用步骤：");
        System.out.println();
        System.out.println("1. 准备DocumentData对象");
        System.out.println("   DocumentData doc = loadYourDocument();");
        System.out.println();
        System.out.println("2. 创建转换器");
        System.out.println("   TiptapConverter converter = new TiptapConverter();");
        System.out.println();
        System.out.println("3. 执行转换");
        System.out.println("   Map<String, Object> result = converter.convert(doc);");
        System.out.println();
        System.out.println("4. 使用结果");
        System.out.println("   - 直接使用Map对象");
        System.out.println("   - 或序列化为JSON: ObjectMapper.writeValueAsString(result)");
        System.out.println();
        System.out.println("转换特性：");
        System.out.println("  ✓ 无损转换 - 所有字段完整保留");
        System.out.println("  ✓ 类型识别 - 自动识别Block类型（HeadingBlock, ParagraphBlock等）");
        System.out.println("  ✓ 列表合并 - 自动合并连续的列表项");
        System.out.println("  ✓ 样式保留 - TextRun的所有样式属性完整转换");
        System.out.println("  ✓ 元信息 - 所有metadata保存在doc.attrs中");
        System.out.println();
        System.out.println("输出结构：");
        System.out.println("  {");
        System.out.println("    \"type\": \"doc\",");
        System.out.println("    \"attrs\": { /* 元信息：title, properties, images等 */ },");
        System.out.println("    \"content\": [ /* 内容节点 */ ]");
        System.out.println("  }");
        System.out.println();
        System.out.println("详细文档请参考：");
        System.out.println("  - README.md - 功能说明和转换规则");
        System.out.println("  - INTEGRATION.md - 集成指南和使用示例");
        System.out.println("  - examples/source.txt - 输入示例");
        System.out.println("  - examples/target.txt - 输出示例");
    }
}
