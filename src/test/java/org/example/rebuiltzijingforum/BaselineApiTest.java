package org.example.rebuiltzijingforum;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class BaselineApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointIsReady() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("UP"));
    }

    @Test
    void featureCatalogContainsElevenSlices() throws Exception {
        mockMvc.perform(get("/api/features"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(11))
                .andExpect(jsonPath("$.data[0].id").value("F01"))
                .andExpect(jsonPath("$.data[10].id").value("F11"));
    }
}
/**
 *模板                                    案例
 * @PostMapping("路径")                         @PostMapping("/login")\
 * Result 方法名 (@RequestBody DTO dto)         Result login(@RequestBody LoginDTO dto)
 */

/**
 * 模板                    案例
 * JWT.create()               JWT.create()
 * .withClaim("键",值)            .withClaim("UserId",1001L)
 * .withExpiresAt(时间)           .withExpiresAt(expireTime)
 * .sign(算法)                    .sign(Algorihm.HMAC256("abc"))
 */