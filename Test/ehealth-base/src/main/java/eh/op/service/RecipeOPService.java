package eh.op.service;

import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.dao.DoctorDAO;
import eh.cdr.dao.RecipeDAO;
import eh.cdr.dao.RecipeDetailDAO;
import eh.cdr.service.RecipeCheckService;
import eh.cdr.service.RecipeService;
import eh.entity.base.Doctor;
import eh.entity.cdr.Recipe;
import eh.entity.cdr.Recipedetail;
import eh.entity.mpi.Patient;
import eh.mpi.dao.PatientDAO;
import eh.op.auth.service.SecurityService;
import eh.utils.DateConversion;

import java.util.*;

/**
 * 处方服务【运营平台】
 *
 * @author jianghc
 * @create 2016-11-10 14:33
 **/
public class RecipeOPService {

    /**
     * 查询处方列表
     * <p>
     * 运营平台（权限改造）
     *
     * @param status   处方状态
     * @param doctor   开方医生
     * @param mpiid    患者主键
     * @param bDate    开始时间
     * @param eDate    结束时间
     * @param dateType 时间类型（0：开方时间，1：审核时间）
     * @param start    分页开始index
     * @param limit    分页长度
     * @return QueryResult<Map>
     */
    @RpcService
    public QueryResult<Map> findRecipesByInfo(Integer organId, Integer status, Integer doctor, String mpiid, Date bDate, Date eDate, Integer dateType, final int start, final int limit) {
        Set<Integer> o = new HashSet<Integer>();
        o.add(organId);
        if (!SecurityService.isAuthoritiedOrgan(o)) {
            return null;
        }
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        return recipeDAO.findRecipesByInfo(organId, status, doctor, mpiid, bDate, eDate, dateType, start, limit);
    }

    /**
     * 根据状态统计
     *
     * @param status   处方状态
     * @param doctor   开方医生
     * @param mpiid    患者主键
     * @param bDate    开始时间
     * @param eDate    结束时间
     * @param dateType 时间类型（0：开方时间，1：审核时间）
     * @param start    分页开始index
     * @param limit    分页长度
     * @return HashMap<String, Integer>
     */
    @RpcService
    public HashMap<String, Integer> getStatisticsByStatus(final Integer organId, final Integer status, final Integer doctor, final String mpiid, final Date bDate, final Date eDate, Integer dateType,
                                                          final int start, final int limit) {
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        return recipeDAO.getStatisticsByStatus(organId, status, doctor, mpiid, bDate, eDate, dateType, start, limit);
    }

    /**
     * 运营平台（权限改造）
     * @param recipeId
     * @return
     */
    @RpcService
    public Map<String,Object> findRecipeAndDetailsAndCheckById(int recipeId){
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        if(recipe==null){
            throw new DAOException("recipe is null");
        }
        Set<Integer> o = new HashSet<Integer>();
        o.add(recipe.getClinicOrgan());
        if (!SecurityService.isAuthoritiedOrgan(o)) {
            return null;
        }
        RecipeCheckService service = AppContextHolder.getBean("recipeCheckService", RecipeCheckService.class);
        return service.findRecipeAndDetailsAndCheckById( recipeId);

    }


}
