package com.project.ecommerce_api.services;

import com.project.ecommerce_api.entities.Category;
import com.project.ecommerce_api.entities.Product;
import com.project.ecommerce_api.entities.ProductImage;
import com.project.ecommerce_api.exceptions.CustomException;
import com.project.ecommerce_api.exceptions.ResourceNotFoundException;
import com.project.ecommerce_api.helpers.CloudinaryService;
import com.project.ecommerce_api.helpers.ResponseUtil;
import com.project.ecommerce_api.models.authDto.response.CustomResponse;
import com.project.ecommerce_api.models.product.CreateProductDto;
import com.project.ecommerce_api.models.product.ProductInfo;
import com.project.ecommerce_api.models.product.UpdateProductDto;
import com.project.ecommerce_api.repositories.CategoryRepository;
import com.project.ecommerce_api.repositories.ProductImageRepository;
import com.project.ecommerce_api.repositories.ProductRepository;
import com.project.ecommerce_api.utilities.ProductStatus;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductImageRepository productImageRepository;

    private final CloudinaryService cloudinaryService;
    private static final Logger logger = LoggerFactory.getLogger(ProductService.class);
    private final CustomUserDetailService customUserDetailService;

    public CustomResponse<List<ProductInfo>> getAllProducts () {
        CustomResponse<List<ProductInfo>> response = new CustomResponse<>();
        List<ProductInfo> productsInfos = new ArrayList<>();

        try {
            List<Product> allProducts = productRepository.findAll();
            for (Product product : allProducts) {
                ProductInfo productInfo = getProductInfo(product);
                productsInfos.add(productInfo);
            }
            response.setSuccess(true);
            response.setStatusCode(HttpStatus.OK);
            response.setMessage("All Products");
            response.setData(productsInfos);
        } catch (Exception e) {
            throw new CustomException("Error retrieve products");
        }
        return response;
    }

    public CustomResponse<ProductInfo> getProductById (UUID id) {
        CustomResponse<ProductInfo> response = new CustomResponse<>();
        ProductInfo productInfo;

        Optional<Product> productOptional = productRepository.findById(id);
        if (productOptional.isEmpty()) return ResponseUtil.createErrorResponse(response, HttpStatus.NOT_FOUND, "product not found");


        Product product = productOptional.get();
        productInfo = getProductInfo(product);

        response.setSuccess(true);
        response.setStatusCode(HttpStatus.FOUND);
        response.setMessage("Product Details");
        response.setData(productInfo);

        return response;
    }

    @Transactional
    public CustomResponse<ProductInfo> addProduct (CreateProductDto createProductDto) {
        CustomResponse<ProductInfo> response = new CustomResponse<>();
        ProductInfo productInfo;

        // Check if category exists
        Optional<Category> categoryOptional = categoryRepository.findById(createProductDto.getCategoryId());
        if (categoryOptional.isEmpty()) {
            return ResponseUtil.createErrorResponse(response, HttpStatus.PRECONDITION_REQUIRED, "Category not found");
        }

        //Check if product exists
        Optional<Product> productOptional = productRepository.findByName(createProductDto.getName());
        if (productOptional.isPresent()) {
            return ResponseUtil.createErrorResponse(response, HttpStatus.CONFLICT, "product exists");
        }

        //Check if image file available
        if (createProductDto.getFile().isEmpty()) {
            return ResponseUtil.createErrorResponse(response, HttpStatus.BAD_REQUEST, "No image file");
        }


        Product product = new Product();
        ProductImage productImage = new ProductImage();

        product.setName(createProductDto.getName());
        product.setDescription(createProductDto.getDescription());
        product.setPrice(createProductDto.getPrice());
        product.setStockQuantity(createProductDto.getStockQuantity());
        product.setCategory(categoryOptional.get());

        if (product.getStockQuantity() == 0) {
            product.setStatus(ProductStatus.OUT_OF_STOCK);
        } else if (product.getStockQuantity() >= 1) {
            product.setStatus(ProductStatus.AVAILABLE);
        }

        try {
            String productImageUrl = cloudinaryService.uploadFile(createProductDto.getFile());

            productImage.setImageUrl(productImageUrl);
            productImage.setProduct(product);
            productImage.setAltText(createProductDto.getAltText());

            Product savedProduct = productRepository.save(product);
            productImageRepository.save(productImage);

            productInfo = getProductInfo(savedProduct);
            response.setSuccess(true);
            response.setMessage("Product Info");
            response.setStatusCode(HttpStatus.CREATED);
            response.setData(productInfo);

            logger.info("Product successfully created: {}", savedProduct.getId());
        } catch (Exception e) {
            logger.error("Error creating product: {}", e.getMessage());
            throw new CustomException("Error creating product");
        }
        return response;
    }

    @Transactional
    public CustomResponse<ProductInfo> updateProduct(UUID productId, Product updatedProductDetails) {
        CustomResponse<ProductInfo> response = new CustomResponse<>();

        // Find the existing product by ID
        Optional<Product> existingProductOptional = productRepository.findById(productId);

        if (existingProductOptional.isEmpty()) {
            // Return an error response if the product is not found
            return ResponseUtil.createErrorResponse(response, HttpStatus.NOT_FOUND, "Product not found");
        }
        Product existingProduct = existingProductOptional.get();

        // Only update fields that are non-null in the updatedProductDetails object
        if (updatedProductDetails.getName() != null) {
            existingProduct.setName(updatedProductDetails.getName());
        }
        if (updatedProductDetails.getDescription() != null) {
            existingProduct.setDescription(updatedProductDetails.getDescription());
        }
        if (updatedProductDetails.getPrice() != null) {
            existingProduct.setPrice(updatedProductDetails.getPrice());
        }

        if (updatedProductDetails.getStockQuantity() != null) {
            existingProduct.setStockQuantity(updatedProductDetails.getStockQuantity());
        }
        if (updatedProductDetails.getCategory() != null) {
            existingProduct.setCategory(updatedProductDetails.getCategory());
        }
        if (updatedProductDetails.getStatus() != null) {
            existingProduct.setStatus(updatedProductDetails.getStatus());
        }

        // Save the updated product (no new product is created since we're updating an existing one)
        try {
            productRepository.save(existingProduct);
            ProductInfo updatedProduct = getProductInfo(existingProduct);

            // Set the response details
            response.setSuccess(true);
            response.setStatusCode(HttpStatus.OK);
            response.setMessage("Product updated successfully");
            response.setData(updatedProduct);

            return response;

        } catch (Exception e) {
            logger.error("Error updating product: {}", e.getMessage());
            throw new CustomException("Error updating product");
        }
    }

    @Transactional
    public CustomResponse<ProductInfo> deleteProduct(UUID productID) {
        CustomResponse<ProductInfo> response = new CustomResponse<>();

        Product product = productRepository.findById(productID)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        // Delete product images in bulk
        int deletedImagesCount = productImageRepository.deleteAllByProduct(product);
        if (deletedImagesCount == 0) {
            logger.info("Product {} did not have any image files to delete", productID);
        }

        ProductInfo productInfo = getProductInfo(product);
        // Delete the product
        productRepository.delete(product);

        response.setSuccess(true);
        response.setStatusCode(HttpStatus.OK);
        response.setMessage("Product and associated images deleted successfully: ");
        response.setData(productInfo);

        return response;
    }


    // ENTITY HELPER FUNCTION/ METHOD
    private ProductInfo getProductInfo(Product product) {
        ProductInfo productInfo;
        List<String> productImageUrls = productImageRepository.findAllByProduct(product)
                .stream()
                .map(ProductImage::getImageUrl)
                .collect(Collectors.toList());

        productInfo = ProductInfo.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .stockQuantity(product.getStockQuantity())
                .category(product.getCategory())
                .productImages(productImageUrls)
                .status(product.getStatus())
                .build();

        return productInfo;
    }
}
