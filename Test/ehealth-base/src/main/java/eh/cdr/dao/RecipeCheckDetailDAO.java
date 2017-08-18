package eh.cdr.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import eh.entity.cdr.RecipeCheckDetail;

import java.util.List;

/**
 * Created by zhongzx on 2016/10/25 0025.
 * 审方详情dao
 */
public abstract class RecipeCheckDetailDAO extends HibernateSupportDelegateDAO<RecipeCheckDetail> {

    public RecipeCheckDetailDAO(){
        super();
        this.setEntityName(RecipeCheckDetail.class.getName());
        this.setKeyField("checkDetailId");
    }

    @DAOMethod(limit = 0)
    public abstract List<RecipeCheckDetail> findByCheckId(Integer checkId);

}
