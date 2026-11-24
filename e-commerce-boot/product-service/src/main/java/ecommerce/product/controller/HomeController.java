package ecommerce.product.controller;

import ecommerce.common.Result;
import ecommerce.product.service.ProductService;
import ecommerce.product.vo.HomeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 首页聚合接口：分类 + 猜你喜欢合并返回（免登录）。
 * <p>
 * 分类走 Redis 缓存（毫秒级），猜你喜欢直接取随机上架商品，与 {@code GET /api/products/random} 同一口径。
 */
@Tag(name = "首页", description = "小程序首页聚合数据")
@RestController
@RequestMapping("/api")
public class HomeController {

    private final ProductService productService;

    public HomeController(ProductService productService) {
        this.productService = productService;
    }

    @Operation(summary = "首页聚合数据（分类 + 猜你喜欢）")
    @GetMapping("/home")
    public Result<HomeVO> home(@RequestParam(defaultValue = "10") Integer limit) {
        return Result.success(HomeVO.builder()
                .categories(productService.listCategories())
                .recommend(productService.listRandomProducts(limit, null))
                .build());
    }
}
