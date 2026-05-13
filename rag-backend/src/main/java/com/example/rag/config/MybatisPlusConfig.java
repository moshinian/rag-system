package com.example.rag.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.OffsetDateTime;

/**
 * MyBatis-Plus 基础配置。
 */
@Configuration
public class MybatisPlusConfig {
    /** 配置 MyBatis-Plus 拦截器。 */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        return interceptor;
    }

    /** 配置实体自动填充处理器。 */
    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            /** 在新增时自动填充审计字段。 */
            @Override
            public void insertFill(MetaObject metaObject) {
                OffsetDateTime now = OffsetDateTime.now();
                strictInsertFill(metaObject, "createdAt", OffsetDateTime.class, now);
                strictInsertFill(metaObject, "updatedAt", OffsetDateTime.class, now);
            }

            /** 在更新时自动填充更新时间字段。 */
            @Override
            public void updateFill(MetaObject metaObject) {
                setFieldValByName("updatedAt", OffsetDateTime.now(), metaObject);
            }
        };
    }
}
