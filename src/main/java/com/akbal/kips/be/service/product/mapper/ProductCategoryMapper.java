package com.akbal.kips.be.service.product.mapper;

import com.akbal.kips.be.domain.product.ProductCategory;
import com.akbal.kips.be.dto.product.response.CategoryResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Component
public class ProductCategoryMapper {
    public CategoryResponse toResponse(ProductCategory category) {
        return CategoryResponse.builder().id(category.getId()).name(category.getName()).build();
    }

    public List<CategoryResponse> toTree(List<ProductCategory> categories) {
        var nodes = new HashMap<Long, CategoryResponse>();
        for (var category : categories) {
            nodes.put(category.getId(), toResponse(category));
        }

        var roots = new ArrayList<CategoryResponse>();
        for (var category : categories) {
            var node = nodes.get(category.getId());
            if (category.getParent() == null) {
                roots.add(node);
            } else {
                appendChild(nodes.get(category.getParent().getId()), node);
            }
        }
        return roots;
    }

    public void appendChild(CategoryResponse parent, CategoryResponse child) {
        if (parent.getChildren() == null) {
            parent.setChildren(new ArrayList<>());
        }
        parent.getChildren().add(child);
    }
}
