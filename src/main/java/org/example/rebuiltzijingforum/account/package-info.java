/**
 * F01 账号注册、登录、JWT 鉴权与修改密码。
 *
 * <p>该功能采用常见的 Spring Boot 分层命名，内部再分为：</p>
 * <ul>
 *   <li>controller：HTTP 接口；</li>
 *   <li>dto：接收和内部传递数据；</li>
 *   <li>vo：返回给前端的数据；</li>
 *   <li>service/impl：业务接口与实现；</li>
 *   <li>mapper/entity：MyBatis 数据访问与表实体；</li>
 *   <li>config/interceptor/utils：配置、拦截器和工具类。</li>
 * </ul>
 *
 * 复现指导见同目录 GUIDE.md。
 */
package org.example.rebuiltzijingforum.account;
