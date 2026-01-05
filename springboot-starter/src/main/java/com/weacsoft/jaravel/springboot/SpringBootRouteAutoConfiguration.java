package com.weacsoft.jaravel.springboot;

import com.weacsoft.jaravel.http.request.Request;
import com.weacsoft.jaravel.http.request.RequestFactory;
import com.weacsoft.jaravel.http.response.Response;
import com.weacsoft.jaravel.middleware.Middleware;
import com.weacsoft.jaravel.route.Route;
import com.weacsoft.jaravel.route.Router;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.function.*;

import java.util.List;

@AutoConfiguration
public class SpringBootRouteAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public Router baseRouter() {
        Router router = new Router();
        return router;
    }

    @Bean
    public RouterFunction<ServerResponse> jaravelRouterFunction(Router router) {
        List<Route> routes = router.getAllRoutes();
        RouterFunctions.Builder builder = RouterFunctions.route();
        routes.forEach(route -> {
            builder.route(createRoutePredicate(route), createRouteFunction(route));
        });
        return builder.build();
    }

    private RequestPredicate createRoutePredicate(Route route) {
        return RequestPredicates.method(HttpMethod.valueOf(route.getMethod()))
                .and(RequestPredicates.path(route.generateFullUri()));
    }

    private HandlerFunction<ServerResponse> createRouteFunction(Route route) {
        return springRequest -> {
            Request customRequest = RequestFactory.buildFromServerRequest(springRequest);
            Middleware.NextFunction finalHandler = route.getAction()::handle;

            for (int i = route.getMiddlewares().size() - 1; i >= 0; i--) {
                final Middleware middleware = route.getMiddlewares().get(i);
                final Middleware.NextFunction next = finalHandler;
                finalHandler = request -> middleware.handle(request, next);
            }

            Response response = finalHandler.apply(customRequest);
            return createResponse(response);
        };
    }

    private ServerResponse createResponse(Response response) {
        ServerResponse.BodyBuilder builder = ServerResponse.status(response.getStatus());
        response.getHeaders().forEach((key, value) -> {
            builder.header(key, value.toArray(new String[0]));
        });
        return builder.body(response.getContent());
    }
}
