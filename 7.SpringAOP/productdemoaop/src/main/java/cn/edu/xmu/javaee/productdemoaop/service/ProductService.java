package cn.edu.xmu.javaee.productdemoaop.service;

import cn.edu.xmu.javaee.core.exception.BusinessException;
import cn.edu.xmu.javaee.core.model.ReturnNo;
import cn.edu.xmu.javaee.core.util.RedisUtil;
import cn.edu.xmu.javaee.productdemoaop.dao.ProductDao;
import cn.edu.xmu.javaee.productdemoaop.dao.bo.Product;
import cn.edu.xmu.javaee.productdemoaop.dao.bo.User;
import cn.edu.xmu.javaee.productdemoaop.mapper.generator.po.OnSalePo;
import cn.edu.xmu.javaee.productdemoaop.mapper.generator.po.ProductPoExample;
import cn.edu.xmu.javaee.productdemoaop.mapper.manual.ProductAllMapper;
import cn.edu.xmu.javaee.productdemoaop.mapper.manual.po.ProductAllPo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.List;

@Service
public class ProductService {

    private static final Logger logger = LoggerFactory.getLogger(ProductService.class);
    private final RedisTemplate<String, Serializable> redisTemplate;
    private final ProductDao productDao;
    private final RedisUtil redisUtil;

    @Autowired
    public ProductService(RedisTemplate<String, Serializable> redisTemplate, ProductDao productDao) {
        this.redisTemplate = redisTemplate;
        this.productDao = productDao;
        this.redisUtil = new RedisUtil(redisTemplate);
    }

    /**
     * 获取某个商品信息，仅展示相关内容
     *
     * @param id 商品id
     * @return 商品对象
     */

   @Transactional(rollbackFor = {BusinessException.class})
    public Product retrieveProductByID(Long id, boolean all) throws BusinessException {
        logger.debug("findProductById: id = {}, all = {}", id, all);
        return productDao.retrieveProductByID(id, all);
    }

    @Transactional(rollbackFor = {BusinessException.class})
    public Product retrieveProductByIDRedis(Long id, boolean all) throws BusinessException {
        logger.debug("findProductById: id = {}, all = {}", id, all);
        return productDao.retrieveProductByIDRedis(id, all);
    }


     /* 用商品名称搜索商品
     *
     * @param name 商品名称
     * @return 商品对象
     */
    @Transactional
    public List<Product> retrieveProductByName(String name, boolean all) throws BusinessException{
        return productDao.retrieveProductByName(name, all);
    }

    /**
     * 新增商品
     * @param product 新商品信息
     * @return 新商品
     */
    @Transactional
    public Product createProduct(Product product, User user) throws BusinessException{
        return productDao.createProduct(product, user);
    }


    /**
     * 修改商品
     * @param product 修改商品信息
     */
    @Transactional
    public void modifyProduct(Product product, User user) throws BusinessException{
        productDao.modiProduct(product, user);
    }

    /** 删除商品
     * @param id 商品id
     * @return 删除是否成功
     */
    @Transactional
    public void deleteProduct(Long id) throws BusinessException {
        productDao.deleteProduct(id);
    }

    @Transactional
    public Product findProductById_manual(Long id) throws BusinessException {
        logger.debug("findProductById_manual: id = {}", id);
        return productDao.findProductByID_manual(id);
    }

   /* @Transactional
    public Product findProductById_redis(Long id) throws BusinessException {
        logger.debug("findProductById_redis: id = {}", id);
        return productDao.findProductByID_Redis(id);
    }*/

    /**
     * 用商品名称搜索商品
     *
     * @param name 商品名称
     * @return 商品对象
     */
    @Transactional
    public List<Product> findProductByName_manual(String name) throws BusinessException{
        return productDao.findProductByName_manual(name);
    }

    public List<Product> getProductByName_join(String name) {
        return productDao.findProductByName_join(name);
    }
}
