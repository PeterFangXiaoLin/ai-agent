package com.example.aiagent.tools;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class FileOperationToolTest {

    @Test
    void readFile() {
        String fileName = "编程导航.txt";
        FileOperationTool tool = new FileOperationTool();
        String result = tool.readFile(fileName);
        Assertions.assertNotNull(result);
    }

    @Test
    void writeFile() {
        String fileName = "编程导航.txt";
        String content = "hello world";
        FileOperationTool tool = new FileOperationTool();
        String result = tool.writeFile(fileName, content);
        Assertions.assertNotNull(result);
    }
}