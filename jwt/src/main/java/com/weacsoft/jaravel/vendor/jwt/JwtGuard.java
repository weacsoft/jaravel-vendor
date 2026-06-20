package com.weacsoft.jaravel.vendor.jwt;

import com.weacsoft.jaravel.vendor.auth.AuthContext;
import com.weacsoft.jaravel.vendor.auth.contract.AuthGuard;
import com.weacsoft.jaravel.vendor.auth.contract.Authenticatable;
import com.weacsoft.jaravel.vendor.auth.contract.UserProvider;
import com.weacsoft.jaravel.vendor.http.request.Request;

/**
 * JWT 守卫，对齐 manage8 的 api guard（jwt 驱动）与 Laravel tymon/jwt-auth。
 * <p>
 * 从请求头解析 Bearer token，按 subject（主键）取出用户；登录时签发 token 并缓存于线程。
 * 支持：
 * <ul>
 *   <li><b>登出黑名单</b>：{@link #logout()} 将当前 token 加入缓存黑名单，后续请求即使携带该 token 也无法通过 {@link #user()}；</li>
 *   <li><b>自动续期</b>：当 {@code refreshEnabled=true}（默认）且 token 已过半 TTL 时，{@link #user()} 自动签发新 token，
 *       可通过 {@link #token()} 获取并返回给客户端；</li>
 *   <li><b>refresh token 换取</b>：{@link #refresh(String)} 用 refresh token 换取新 access token。</li>
 * </ul>
 *
 * <h3>线程安全</h3>
 * 本守卫实例由 {@link com.weacsoft.jaravel.vendor.auth.AuthManager} 通过 ThreadLocal 按请求隔离，
 * 每个请求获得独立的 JwtGuard 实例（{@code cachedUser}、{@code resolved}、{@code lastToken}、
 * {@code requestToken} 均为请求级状态，不跨请求共享）。{@link JwtService} 为无状态单例，可安全并发调用。
 */
public class JwtGuard implements AuthGuard {

    private final String name;
    private final UserProvider provider;
    private final JwtService jwtService;
    private final boolean refreshEnabled;

    /** 当前请求解析出的用户（请求级缓存） */
    private Authenticatable cachedUser;
    /** 是否已解析过当前请求 */
    private boolean resolved = false;
    /** 最近一次签发的 token（login 或自动续期产生），供 {@link #token()} 返回 */
    private String lastToken;
    /** 当前请求携带的 token（从 Authorization 头解析），供 {@link #logout()} 加入黑名单 */
    private String requestToken;

    public JwtGuard(String name, UserProvider provider, JwtService jwtService, boolean refreshEnabled) {
        this.name = name;
        this.provider = provider;
        this.jwtService = jwtService;
        this.refreshEnabled = refreshEnabled;
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
     *   <li>按 subject（主键）通过 {@link UserProvider#retrieveById} 取出用户；</li>
     *   <li>若 {@code refreshEnabled=true} 且 token 已过半 TTL（{@link JwtService#shouldRefresh}），
     *       自动签发新 token 并存入 {@link #lastToken}，客户端可通过 {@link #token()} 获取。</li>
     * </ol>
     */
    @Override
    public Authenticatable user() {
        if (resolved) return cachedUser;
        resolved = true;
        Request req = AuthContext.get();
        if (req == null) return null;
        String token = extractBearerToken(req);
        if (token == null || !jwtService.validate(token)) {
            return null;
        }
        requestToken = token;
        String subject = jwtService.getSubject(token);
        cachedUser = provider.retrieveById(subject);

        // 自动续期：token 已过半 TTL 时签发新 token
        if (cachedUser != null && refreshEnabled && jwtService.shouldRefresh(token)) {
            lastToken = jwtService.generate(String.valueOf(cachedUser.getAuthIdentifier()));
        }
        return cachedUser;
    }

    /**
     * 登录指定用户：签发 access token 与 refresh token，缓存用户。
     * <p>
     * 签发的 access token 可通过 {@link #token()} 获取；refresh token 可通过
     * {@link #refreshToken()} 获取，客户端可用它调用 {@link #refresh(String)} 续期。
     */
    @Override
    public void login(Authenticatable user) {
        cachedUser = user;
        resolved = true;
        lastToken = jwtService.generate(String.valueOf(user.getAuthIdentifier()));
    }

    /**
     * 登出：将当前请求的 token 加入黑名单，并清理请求级状态。
     * <p>
     * 加入黑名单后，该 token 即使仍在有效期内，后续请求也无法通过 {@link #user()} 校验，
     * 对齐 Laravel tymon/jwt-auth 的 {@code invalidate}。
     */
    @Override
    public void logout() {
        // 将请求携带的 token 加入黑名单（若存在）
        if (requestToken != null) {
            jwtService.blacklist(requestToken);
        } else {
            // 未解析过 user() 时，尝试从请求头提取并加入黑名单
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
     * 包括：{@link #login} 签发的 token，或 {@link #user()} 自动续期签发的新 token。
     * 若本次请求既未登录也未触发自动续期，返回 {@code null}（客户端继续使用原有 token 即可）。
     */
    @Override
    public String token() {
        return lastToken;
    }

    /**
     * 签发 refresh token（登录后调用）。
     *
     * @return refresh token 字符串，未登录时返回 {@code null}
     */
    public String refreshToken() {
        Authenticatable u = user();
        if (u == null) return null;
        return jwtService.generateRefreshToken(String.valueOf(u.getAuthIdentifier()));
    }

    /**
     * 判断指定 token 是否应当刷新（已过半 TTL）。
     *
     * @param token access token
     * @return 是否应当刷新
     */
    public boolean shouldRefresh(String token) {
        return jwtService.shouldRefresh(token);
    }

    /**
     * 使用 refresh token 换取新的 access token。
     * <p>
     * 校验 refresh token 通过后签发新 access token，并将其存入 {@link #lastToken}（可通过 {@link #token()} 获取）。
     * 同时按 subject 取出用户并缓存。
     *
     * @param refreshToken refresh token
     * @return 新的 access token，校验失败返回 {@code null}
     */
    public String refresh(String refreshToken) {
        String accessToken = jwtService.refresh(refreshToken);
        if (accessToken == null) {
            return null;
        }
        lastToken = accessToken;
        // 取出用户并缓存
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
