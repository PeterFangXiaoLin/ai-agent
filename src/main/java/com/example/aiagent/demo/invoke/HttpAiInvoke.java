package com.example.aiagent.demo.invoke;

import cn.hutool.http.ContentType;
import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

/**
 * 使用 Hutool 调用阿里云百炼文本生成接口。
 */
public class HttpAiInvoke {

    private static final String API_URL =
            "https://llm-ypbosy771gwtagts.cn-beijing.maas.aliyuncs.com/api/v1/services/aigc/text-generation/generation";

    private static final String MODEL_NAME = "qwen3.7-max";

    private HttpAiInvoke() {
    }

    public static String invoke(String prompt) {
        String apiKey = TestApiKey.API_KEY;

        JSONArray messages = JSONUtil.createArray();
        messages.add(JSONUtil.createObj()
                .set("role", "user")
                .set("content", prompt));

        JSONObject requestBody = JSONUtil.createObj()
                .set("model", MODEL_NAME)
                .set("input", JSONUtil.createObj()
                        .set("messages", messages))
                .set("parameters", JSONUtil.createObj()
                        .set("enable_thinking", true)
                        .set("result_format", "message"));

        try (HttpResponse response = HttpRequest.post(API_URL)
                .header(Header.AUTHORIZATION, "Bearer " + apiKey)
                .header(Header.CONTENT_TYPE, ContentType.JSON.getValue())
                .body(requestBody.toString())
                .timeout(60_000)
                .execute()) {
            String responseBody = response.body();
            if (!response.isOk()) {
                throw new IllegalStateException(
                        "调用大模型失败，HTTP " + response.getStatus() + "：" + responseBody);
            }
            return responseBody;
        }
    }

    public static void main(String[] args) {
        System.out.println(invoke("你是谁？"));
    }
}
