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
        String response1 = loveApp.doChat("你好，我是小明", chatId);
        Assertions.assertNotNull(response1);
        String response2 = loveApp.doChat("我想让另一半（小红）更爱我", chatId);
        Assertions.assertNotNull(response2);
        String response3 = loveApp.doChat("我的另一半是谁，刚跟你说过，帮我回忆一下", chatId);
        Assertions.assertNotNull(response3);
    }
}
