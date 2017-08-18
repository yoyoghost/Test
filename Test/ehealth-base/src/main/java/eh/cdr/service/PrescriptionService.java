package eh.cdr.service;

import ctd.persistence.DAOFactory;
import ctd.util.annotation.RpcService;
import eh.bus.dao.OperationRecordsDAO;
import eh.entity.cdr.Recipe;
import eh.entity.cdr.Recipedetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Description: 合理用药自动审查服务
 * Created by chuw@winning.com
 * Created Date 2017/4/21 9:25
 */
public class PrescriptionService {
    private static final Logger logger = LoggerFactory.getLogger(PrescriptionService.class);

    @RpcService
    public void prescriptionScreen(Recipe recipe, List<Recipedetail> details) {
        if(null == details){
            details = new ArrayList<>(0);
        }

        int recipeId = recipe.getRecipeId();
        OperationRecordsDAO operationRecordsDAO = DAOFactory.getDAO(OperationRecordsDAO.class);
        operationRecordsDAO.saveOperationRecordsForRecipe(recipe);
        RecipeLogService.saveRecipeLog(recipeId, recipe.getStatus(), recipe.getStatus(),"处方合理性审核");
    }

}
