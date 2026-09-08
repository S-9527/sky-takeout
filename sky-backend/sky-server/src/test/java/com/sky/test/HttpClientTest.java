package com.sky.test;

import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

public class HttpClientTest {

    private final RestClient restClient = RestClient.create();

    /**
     * 测试通过RestClient发送GET方式的请求
     */
    @Test
    public void testGET() {
        String body = restClient.get()
                .uri("http://localhost:8080/user/shop/status")
                .retrieve()
                .body(String.class);
        System.out.println("服务端返回的数据为：" + body);
    }


    /**
     * 测试通过RestClient发送POST方式的请求
     */
    @Test
    public void testPOST() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("username", "admin");
        jsonObject.put("password", "123456");

        String body = restClient.post()
                .uri("http://localhost:8080/admin/employee/login")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(jsonObject.toString())
                .retrieve()
                .body(String.class);
        System.out.println("响应数据为：" + body);
    }
}