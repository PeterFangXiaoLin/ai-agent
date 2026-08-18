package com.example.aiagent;

import com.example.aiagent.app.LoveApp;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

@SpringBootTest
public class LoveAppTest {

    @Resource
    private LoveApp loveApp;

    @Test
    void testChat() {
        String chatId = UUID.randomUUID().toString();
        // 第一轮
        String response1 = loveApp.doChat("你好，我是小明", chatId);
        Assertions.assertNotNull(response1);
        // 第二轮
        String response2 = loveApp.doChat("我想让另一半（小红）更爱我", chatId);
        Assertions.assertNotNull(response2);
        // 第三轮
        String response3 = loveApp.doChat("我的另一半是谁，刚跟你说过，帮我回忆一下", chatId);
        Assertions.assertNotNull(response3);
    }

    @Test
    void doChatWithReport() {
        String chatId = UUID.randomUUID().toString();
        // 第一轮
        String message = "你好，我是小明，我想让另一半（小红）更爱我，但我不知道该怎么做";
        LoveApp.LoveReport loveReport = loveApp.doChatWithReport(message, chatId);
        Assertions.assertNotNull(loveReport);
    }

}
