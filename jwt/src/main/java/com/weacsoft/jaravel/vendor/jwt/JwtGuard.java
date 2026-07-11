package com.weacsoft.jaravel.vendor.jwt;

import com.weacsoft.jaravel.vendor.auth.AuthContext;
import com.weacsoft.jaravel.vendor.auth.contract.AuthGuard;
import com.weacsoft.jaravel.vendor.auth.contract.Authenticatable;
import com.weacsoft.jaravel.vendor.auth.contract.UserProvider;
import com.weacsoft.jaravel.vendor.http.controller.request.Request;

/**
 * JWT 守卫，对齐 manage8 的 api guard（jwt 驱动）与 Laravel tymon/jwt-auth。
 * <p>
 * 从请求头解析 Bearer token，按 subject（主键）取出用户；登录时签发 token 并缓存于线程。
 * 支持：
 * <ul>
 *   <li><b>登出黑名单</b>（需 {@code blacklistEnabled=true}）：{@link #logout()} 将当前 token 加入缓存黑名单；</li>
 *   <li><b>自动续期</b>：当 {@code refreshEnabled=true}（默认）且 token 已过半 TTL 时，自动签发新 token；</li>
 *   <li><b>宽限期续期</b>（需 {@code blacklistEnabled=true} 且 {@code gracePeriodSeconds>0}）：
 *       过期 token 在宽限期内仍可请求一次，请求正常执行后在响应 header 中携带新 token
 *       （通过 {@link JwtConfig#getGraceHeader()} 指定的响应头），旧 token 被加入黑名单；</li>
 *   <li><b>refresh token 换取</b>：{@link #refresh(String)} 用 refresh token 换取新 access token。</li>
 * </ul>
 *
 * <h3>响应 header 中的新 token</h3>
 * 当自动续期或宽限期续期触发时，新 token 可通过 {@link #token()} 获取。
 * {@link JwtTokenResponseFilter} 会在请求结束时自动将新 token 写入响应 header。
 *
 * <h3>线程安全</h3>
 * 本守卫实例由 {@link com.weacsoft.jaravel.vendor.auth.AuthManager} 通过 ThreadLocal 按请求隔离，
 * 每个请求获得独立的 JwtGuard 实例。{@link JwtService} 为无状态单例，可安全并发调用。
 */
public class JwtGuard implements AuthGuard {

    private final String name;
    private final UserProvider provider;
    private final JwtService jwtService;
    private final boolean refreshEnabled;
    private final JwtConfig jwtConfig;

    /** 当前请求解析出的用户（请求级缓存） */
    private Authenticatable cachedUser;
    /** 是否已解析过当前请求 */
    private boolean resolved = false;
    /** 最近一次签发的 token（login 或自动续期或宽限期续期产生），供 {@link #token()} 返回 */
    private String lastToken;
    /** 当前请求携带的 token（从 Authorization 头解析），供 {@link #logout()} 加入黑名单 */
    private String requestToken;

    public JwtGuard(String name, UserProvider provider, JwtService jwtService, boolean refreshEnabled) {
        this.name = name;
        this.provider = provider;
        this.jwtService = jwtService;
        this.refreshEnabled = refreshEnabled;
        this.jwtConfig = null;
    }

    public JwtGuard(String name, UserProvider provider, JwtService jwtService, boolean refreshEnabled, JwtConfig jwtConfig) {
        this.name = name;
        this.provider = provider;
        this.jwtService = jwtService;
        this.refreshEnabled = refreshEnabled;
        this.jwtConfig = jwtConfig;
    }

    /** 兼容旧构造器：默认启用自动续期 */
    public JwtGuard(String name, UserProvider provider, JwtService jwtService) {
        this(name, provider, jwtService, true);
    }

    @Override
    public boolean check() {
        return user() != null;
    }

    @Override
    public boolean guest() {
        return !check();
    }

    /**
     * 从请求头 Authorization 提取 Bearer token。
     */
    private String extractBearerToken(Request req) {
        if (req == null) return null;
        String header = req.header("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return null;
    }

    /**
     * 解析当前请求的用户。
     * <p>
     * 流程：
     * <ol>
     *   <li>从 Authorization 头提取 Bearer token；</li>
     *   <li>校验 token（签名 + 过期 + 黑名单，见 {@link JwtService#validate}）；</li>
     *   <li>若校验通过，按 subject 通过 {@link UserProvider#retrieveById} 取出用户
     *       （每次从数据库查询，不缓存用户对象）；</li>
     *   <li>若 {@code refreshEnabled=true} 且 token 已过半 TTL，自动签发新 token；</li>
     *   <li>若校验未通过但 token 处于宽限期内，允许请求一次：取出用户、签发新 token、
     *       将旧 token 加入黑名单。</li>
     * </ol>
     */
    @Override
    public Authenticatable user() {
        if (resolved) return cachedUser;
        resolved = true;
        Request req = AuthContext.get();
        if (req == null) return null;
        String token = extractBearerToken(req);
        if (token == null) return null;

        // 1. 正常校验
        if (jwtService.validate(token)) {
            requestToken = token;
            String subject = jwtService.getSubject(token);
            cachedUser = provider.retrieveById(subject);

            // 自动续期：token 已过半 TTL 时签发新 token
            if (cachedUser != null && refreshEnabled && jwtService.shouldRefresh(token)) {
                lastToken = jwtService.generate(String.valueOf(cachedUser.getAuthIdentifier()));
            }
            return cachedUser;
        }

        // 2. 宽限期：token 已过期但在宽限期窗口内
        if (jwtService.isInGracePeriod(token) && !jwtService.isBlacklisted(token)) {
            requestToken = token;
            String subject = jwtService.getSubjectFromExpired(token);
            if (subject != null) {
                cachedUser = provider.retrieveById(subject);
                if (cachedUser != null) {
                    // 签发新 token
                    lastToken = jwtService.generate(String.valueOf(cachedUser.getAuthIdentifier()));
                    // 将旧 token 加入黑名单，防止宽限期内重复使用
                    jwtService.blacklist(token);
                }
            }
            return cachedUser;
        }

        return null;
    }

    /**
     * 登录指定用户：签发 access token 与 refresh token，缓存用户。
     */
    @Override
    public void login(Authenticatable user) {
        cachedUser = user;
        resolved = true;
        lastToken = jwtService.generate(String.valueOf(user.getAuthIdentifier()));
    }

    /**
     * 登出：将当前请求的 token 加入黑名单（需 {@code blacklistEnabled=true}），并清理请求级状态。
     * <p>
     * 加入黑名单后，该 token 后续请求无法通过校验。
     * 当 {@code blacklistEnabled=false} 时仅清理请求级状态（标准 JWT 无登出踢 token 能力）。
     */
    @Override
    public void logout() {
        if (requestToken != null) {
            jwtService.blacklist(requestToken);
        } else {
            Request req = AuthContext.get();
            String token = extractBearerToken(req);
            if (token != null) {
                jwtService.blacklist(token);
            }
        }
        cachedUser = null;
        resolved = true;
        lastToken = null;
        requestToken = null;
    }

    /**
     * 获取最近一次签发的 access token。
     * <p>
     * 包括：{@link #login} 签发的 token，或 {@link #user()} 自动续期/宽限期续期签发的新 token。
     * 若本次请求既未登录也未触发续期，返回 {@code null}。
     * <p>
     * {@link JwtTokenResponseFilter} 会在请求结束时自动将此 token 写入响应 header。
     */
    @Override
    public String token() {
        return lastToken;
    }

    /**
     * 签发 refresh token（登录后调用）。
     */
    public String refreshToken() {
        Authenticatable u = user();
        if (u == null) return null;
        return jwtService.generateRefreshToken(String.valueOf(u.getAuthIdentifier()));
    }

    /**
     * 判断指定 token 是否应当刷新（已过半 TTL）。
     */
    public boolean shouldRefresh(String token) {
        return jwtService.shouldRefresh(token);
    }

    /**
     * 使用 refresh token 换取新的 access token。
     */
    public String refresh(String refreshToken) {
        String accessToken = jwtService.refresh(refreshToken);
        if (accessToken == null) {
            return null;
        }
        lastToken = accessToken;
        String subject = jwtService.getSubject(accessToken);
        cachedUser = provider.retrieveById(subject);
        resolved = true;
        return accessToken;
    }

    /** 守卫名称 */
    public String getName() {
        return name;
    }
}
