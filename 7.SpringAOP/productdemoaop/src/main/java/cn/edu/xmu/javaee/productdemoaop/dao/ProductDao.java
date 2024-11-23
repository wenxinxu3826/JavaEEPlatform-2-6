//School of Informatics Xiamen University, GPL-3.0 license
package cn.edu.xmu.javaee.productdemoaop.dao;

import cn.edu.xmu.javaee.core.exception.BusinessException;
import cn.edu.xmu.javaee.core.model.ReturnNo;
import cn.edu.xmu.javaee.core.util.RedisUtil;
import cn.edu.xmu.javaee.productdemoaop.dao.bo.OnSale;
import cn.edu.xmu.javaee.productdemoaop.dao.bo.Product;
import cn.edu.xmu.javaee.productdemoaop.dao.bo.User;
import cn.edu.xmu.javaee.productdemoaop.mapper.generator.ProductPoMapper;
import cn.edu.xmu.javaee.productdemoaop.mapper.generator.po.ProductPo;
import cn.edu.xmu.javaee.productdemoaop.mapper.generator.po.ProductPoExample;
import cn.edu.xmu.javaee.productdemoaop.mapper.manual.ProductAllMapper;
import cn.edu.xmu.javaee.productdemoaop.mapper.manual.po.ProductAllPo;
import cn.edu.xmu.javaee.productdemoaop.util.CloneFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Ming Qiu
 **/
@Repository
public class ProductDao {

    private final static Logger logger = LoggerFactory.getLogger(ProductDao.class);

    private ProductPoMapper productPoMapper;

    private OnSaleDao onSaleDao;

    private ProductAllMapper productAllMapper;
    private final RedisTemplate<String, Serializable> redisTemplate;

    private final RedisUtil redisUtil;

    @Autowired
    public ProductDao(ProductPoMapper productPoMapper, OnSaleDao onSaleDao, ProductAllMapper productAllMapper, RedisTemplate<String, Serializable> redisTemplate) {
        this.productPoMapper = productPoMapper;
        this.onSaleDao = onSaleDao;
        this.productAllMapper = productAllMapper;
        this.redisTemplate = redisTemplate;
        this.redisUtil = new RedisUtil(redisTemplate);
    }

    /**
     * 用GoodsPo对象找Goods对象
     * @param name
     * @return  Goods对象列表，带关联的Product返回
     */
    public List<Product> retrieveProductByName(String name, boolean all) throws BusinessException {
        List<Product> productList = new ArrayList<>();
        ProductPoExample example = new ProductPoExample();
        ProductPoExample.Criteria criteria = example.createCriteria();
        criteria.andNameEqualTo(name);
        List<ProductPo> productPoList = productPoMapper.selectByExample(example);
        for (ProductPo po : productPoList){
            Product product = null;
            if (all) {
                product = this.retrieveFullProduct(po);
            } else {
                product = CloneFactory.copy(new Product(), po);
            }
            productList.add(product);
        }
        logger.debug("retrieveProductByName: productList = {}", productList);
        return productList;
    }

    /**
     * 用GoodsPo对象找Goods对象
     * @param  productId
     * @return  Goods对象列表，带关联的Product返回
     */
    public Product retrieveProductByID(Long productId, boolean all) throws BusinessException {
        Product product = null;
        ProductPo productPo = productPoMapper.selectByPrimaryKey(productId);
        if (null == productPo){
            throw new BusinessException(ReturnNo.RESOURCE_ID_NOTEXIST, "产品id不存在");
        }
        if (all) {
            product = this.retrieveFullProduct(productPo);
        } else {
            product = CloneFactory.copy(new Product(), productPo);
        }

        logger.debug("retrieveProductByID: product = {}",  product);
        return product;
    }


    /*private Product retrieveFullProduct(ProductPo productPo) throws DataAccessException{
        assert productPo != null;
        Product product =  CloneFactory.copy(new Product(), productPo);
        List<OnSale> latestOnSale = onSaleDao.getLatestOnSale(productPo.getId());
        product.setOnSaleList(latestOnSale);

        List<Product> otherProduct = this.retrieveOtherProduct(productPo);
        product.setOtherProduct(otherProduct);

        return product;
    }*/
    private Product retrieveFullProduct(ProductPo productPo) throws DataAccessException {
        assert productPo != null;
        Product product = CloneFactory.copy(new Product(), productPo);
        String cacheKey = "onsale:" + productPo.getId();
        // 尝试从Redis中获取数据
        List<OnSale> latestOnSale;
        if(redisUtil.hasKey(cacheKey)) {
            latestOnSale=(List<OnSale>)redisUtil.get(cacheKey);
            logger.debug("success find onsale in redis");
        }
         else {
            logger.debug("not found onsale in redis");
            // Redis中没有找到，从数据库查询
            latestOnSale = onSaleDao.getLatestOnSale(productPo.getId());
            if (latestOnSale != null) {
                // 数据库查询到数据后，将其存入Redis
                redisUtil.set(cacheKey, (Serializable) latestOnSale,1000);
            }
        }
        product.setOnSaleList(latestOnSale);
        List<Product> otherProduct = this.retrieveOtherProduct(productPo);
        product.setOtherProduct(otherProduct);
        return product;
    }

    /*private List<Product> retrieveOtherProduct(ProductPo productPo) throws DataAccessException{
        assert productPo != null;

        ProductPoExample example = new ProductPoExample();
        ProductPoExample.Criteria criteria = example.createCriteria();
        criteria.andGoodsIdEqualTo(productPo.getGoodsId());
        criteria.andIdNotEqualTo(productPo.getId());
        List<ProductPo> productPoList = productPoMapper.selectByExample(example);
        return productPoList.stream().map(po->CloneFactory.copy(new Product(), po)).collect(Collectors.toList());
    }*/

    private List<Product> retrieveOtherProduct(ProductPo productPo) throws DataAccessException {
        assert productPo != null;
        ProductPoExample example = new ProductPoExample();
        ProductPoExample.Criteria criteria = example.createCriteria();
        criteria.andGoodsIdEqualTo(productPo.getGoodsId());
        criteria.andIdNotEqualTo(productPo.getId());
        String cacheKey = "product:" + productPo.getGoodsId();
        // 尝试从Redis中获取数据
        List<ProductPo> productPoList;
        if(redisUtil.hasKey(cacheKey)){
            productPoList=(List<ProductPo>) redisUtil.get(cacheKey);
            logger.debug("success find otherProduct in redis");
        }
         else {
            logger.debug("not found otherProduct in redis");
            // Redis中没有找到，从数据库查询
            productPoList = productPoMapper.selectByExample(example);
            if (productPoList != null) {
                // 数据库查询到数据后，将其存入Redis
                redisUtil.set(cacheKey, (Serializable) productPoList,1000);
            }
        }
        return productPoList.stream().map(po->CloneFactory.copy(new Product(), po)).collect(Collectors.toList());
    }
    /**
     * 创建Goods对象
     * @param product 传入的Goods对象
     * @return 返回对象ReturnObj
     */
    public Product createProduct(Product product, User user) throws BusinessException{

        Product retObj = null;
        product.setCreator(user);
        product.setGmtCreate(LocalDateTime.now());
        ProductPo po = CloneFactory.copy(new ProductPo(), product);
        int ret = productPoMapper.insertSelective(po);
        retObj = CloneFactory.copy(new Product(), po);
        return retObj;
    }

    /**
     * 修改商品信息
     * @param product 传入的product对象
     * @return void
     */
    public void modiProduct(Product product, User user) throws BusinessException{
        product.setGmtModified(LocalDateTime.now());
        product.setModifier(user);
        ProductPo po = CloneFactory.copy(new ProductPo(), product);
        int ret = productPoMapper.updateByPrimaryKeySelective(po);
        if (ret == 0 ){
            throw new BusinessException(ReturnNo.RESOURCE_ID_NOTEXIST);
        }
    }

    /**
     * 删除商品，连带规格
     * @param id 商品id
     * @return
     */
    public void deleteProduct(Long id) throws BusinessException{
        int ret = productPoMapper.deleteByPrimaryKey(id);
        if (ret == 0) {
            throw new BusinessException(ReturnNo.RESOURCE_ID_NOTEXIST);
        }
    }

    public List<Product> findProductByName_manual(String name) throws BusinessException {
        List<Product> productList;
        ProductPoExample example = new ProductPoExample();
        ProductPoExample.Criteria criteria = example.createCriteria();
        criteria.andNameEqualTo(name);
        List<ProductAllPo> productPoList = productAllMapper.getProductWithAll(example);
        productList =  productPoList.stream().map(o->CloneFactory.copy(new Product(), o)).collect(Collectors.toList());
        logger.debug("findProductByName_manual: productList = {}", productList);
        return productList;
    }

    /**
     * 用GoodsPo对象找Goods对象
     * @param  productId
     * @return  Goods对象列表，带关联的Product返回
     */
    public Product findProductByID_manual(Long productId) throws BusinessException {
        Product product = null;
        ProductPoExample example = new ProductPoExample();
        ProductPoExample.Criteria criteria = example.createCriteria();
        criteria.andIdEqualTo(productId);
        List<ProductAllPo> productPoList = productAllMapper.getProductWithAll(example);

        if (productPoList.size() == 0){
            throw new BusinessException(ReturnNo.RESOURCE_ID_NOTEXIST, "产品id不存在");
        }
        product = CloneFactory.copy(new Product(), productPoList.get(0));
        logger.debug("findProductByID_manual: product = {}", product);
        return product;
    }

    public List<Product> findProductByName_join(String name) throws BusinessException {
        List<Product> productList;
        ProductPoExample example = new ProductPoExample();
        ProductPoExample.Criteria criteria = example.createCriteria();
        criteria.andNameEqualTo(name);
        List<ProductAllPo> productPoList = productAllMapper.getProductbyName_join(name);
        productList =  productPoList.stream().map(o->CloneFactory.copy(new Product(), o)).collect(Collectors.toList());
        logger.debug("findProductByName_join: productList = {}", productList);
        return productList;
    }
}
