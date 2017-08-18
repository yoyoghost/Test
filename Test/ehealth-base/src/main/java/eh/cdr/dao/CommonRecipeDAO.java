package eh.cdr.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import eh.entity.cdr.CommonRecipe;

import java.util.List;

/**
 * Created by jiangtingfeng on 2017/5/22.
 */
public abstract class CommonRecipeDAO extends HibernateSupportDelegateDAO<CommonRecipe> {

    public CommonRecipeDAO() {
        super();
        this.setEntityName(CommonRecipe.class.getName());
        this.setKeyField("commonRecipeId");
    }

    /**
     * 通过处方类型查询常用方列表
     *
     * @param recipeType
     * @return
     */
    @DAOMethod(sql = "from CommonRecipe where recipeType=:recipeType and doctorId=:doctorId order by createDt desc")
    public abstract List<CommonRecipe> findByRecipeType(@DAOParam("recipeType") String recipeType ,
                                                        @DAOParam("doctorId") Integer doctorId,
                                                        @DAOParam(pageStart = true) int start,
                                                        @DAOParam(pageLimit = true) int limit);

    @DAOMethod(sql = "from CommonRecipe where doctorId=:doctorId order by createDt desc")
    public abstract List<CommonRecipe> findByDoctorId(@DAOParam("doctorId") Integer doctorId,
                                                      @DAOParam(pageStart = true) int start,
                                                      @DAOParam(pageLimit = true) int limit);


}