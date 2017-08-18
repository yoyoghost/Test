package eh.cdr.service;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.cdr.constant.RecipeStatusConstant;
import eh.cdr.dao.RecipeLogDAO;
import eh.entity.cdr.RecipeLog;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.joda.time.DateTime;

import java.util.List;

/**
 * 处方状态记录
 * company: ngarihealth
 * author: 0184/yu_yun
 * date:2016/5/27.
 */
public class RecipeLogService {

    private static final Log logger = LogFactory.getLog(RecipeLogService.class);

    /**
     * 保存处方操作记录
     *
     * @param log
     */
    public static void saveRecipeLog(RecipeLog log) {
        if (null == log || null == log.getRecipeId()) {
            return;
        }

        RecipeLogDAO dao = DAOFactory.getDAO(RecipeLogDAO.class);

        if (null == log.getBeforeStatus()) {
            log.setBeforeStatus(RecipeStatusConstant.UNKNOW);
        }
        if (null == log.getAfterStatus()) {
            log.setAfterStatus(RecipeStatusConstant.UNKNOW);
        }
        log.setMemo(StringUtils.defaultString(log.getMemo(), ""));
        log.setExpand(StringUtils.defaultString(log.getExpand(), ""));
        logger.info("saveRecipeLog : " + JSONUtils.toString(log));
        dao.saveRecipeLog(log);
    }

    public static void saveRecipeLog(Integer recipeId, Integer beforeStatus, Integer afterStatus, String memo) {
        RecipeLog recipeLog = new RecipeLog();
        recipeLog.setRecipeId(recipeId);
        recipeLog.setModifyDate(DateTime.now().toDate());
        recipeLog.setBeforeStatus(beforeStatus);
        recipeLog.setAfterStatus(afterStatus);
        recipeLog.setMemo(memo);
        saveRecipeLog(recipeLog);
    }

    @RpcService
    public List<RecipeLog> findByRecipeId(Integer recipeId) {
        if (recipeId==null){
            throw new DAOException(DAOException.VALUE_NEEDED,"recipeId is require");
        }
        RecipeLogDAO recipeLogDAO = DAOFactory.getDAO(RecipeLogDAO.class);
        return recipeLogDAO.findByRecipeId(recipeId);
    }


}
