package eh.base.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.impl.dictionary.DBDictionaryItemLoader;
import eh.entity.base.DrugProducer;

import java.util.List;

/**
 * Created by zhongzx on 2016/7/4 0004.
 * 药品产地 dao
 */
public abstract class DrugProducerDAO extends HibernateSupportDelegateDAO<DrugProducer>
        implements DBDictionaryItemLoader<DrugProducer> {

    public DrugProducerDAO(){
        super();
        this.setEntityName(DrugProducer.class.getName());
        this.setKeyField("id");
    }


    /**
     * zhongzx
     * 根据机构和产地名字 查找产地
     * @param name
     * @param organ
     * @return
     */
    @DAOMethod
    public abstract List<DrugProducer> findByNameAndOrgan(String name, Integer organ);

}

