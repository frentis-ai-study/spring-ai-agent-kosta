package com.kosta.agent.tool;

import com.kosta.agent.domain.ProductRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 상품 카탈로그/재고 도구. 모델이 "재고 있나요?", "가격 얼마예요?" 같은 의도에서 호출한다.
 */
@Component
public class CatalogTools {

    private final ProductRepository products;

    public CatalogTools(ProductRepository products) {
        this.products = products;
    }

    public record StockView(String name, String price, int stockQuantity, boolean inStock) {}

    @Tool(description = "상품명 키워드로 재고 수량과 가격을 조회한다. 부분 일치로 검색하여 여러 후보를 반환할 수 있다.")
    public List<StockView> checkInventory(
            @ToolParam(description = "상품명 또는 일부 키워드 (예: 키보드, 모니터, 허브)") String keyword) {
        return products.findByNameContainingIgnoreCase(keyword).stream()
                .map(p -> new StockView(
                        p.getName(),
                        p.getPrice().toPlainString(),
                        p.getStockQuantity(),
                        p.inStock()))
                .toList();
    }
}
