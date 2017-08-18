package eh.base.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.annotation.RpcService;
import eh.entity.base.Material;

import java.util.List;


public abstract class MaterialDAO extends HibernateSupportDelegateDAO<Material> {

    public MaterialDAO() {
        super();
        setEntityName(Material.class.getName());
        setKeyField("materialId");
    }

    @RpcService
    @DAOMethod(sql = " from Material where materialType =:materialType and materialStatus =1",limit = 0)
    public abstract List<Material> findEffectiveMaterialsByMaterialType(@DAOParam("materialType")String materialType);



}
