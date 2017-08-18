package eh.task.executor;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.cdr.dao.DocIndexDAO;
import eh.cdr.dao.RecipeDAO;
import eh.cdr.dao.RecipeDetailDAO;
import eh.entity.cdr.DocIndex;
import eh.entity.cdr.Recipe;
import eh.entity.cdr.Recipedetail;
import eh.task.ActionExecutor;
import eh.task.ExecutorRegister;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.StatelessSession;

import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 保存处方单列表
 *
 * @author LF
 */
public class SaveRecipeExecutor implements ActionExecutor {
    private static final Log logger = LogFactory.getLog(SaveRecipeExecutor.class);
    /**
     * 线程池
     */
    private static ExecutorService executors = ExecutorRegister.register(Executors.newScheduledThreadPool(2));

    /**
     * 业务参数
     */
    private Recipe recipe;
    private List<Recipedetail> rcplist;

    public SaveRecipeExecutor(Recipe recipe, List<Recipedetail> rcplist) {
        this.recipe = recipe;
        this.rcplist = rcplist;
    }

    @Override
    public void execute() throws DAOException {
        executors.execute(new Runnable() {
            @Override
            public void run() {
                saveRecipes();
            }
        });
    }

    @SuppressWarnings("rawtypes")
    @RpcService
    private void saveRecipes() throws DAOException {
        HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
            public void execute(StatelessSession ss) throws Exception {
                //保存检验报告列表
                RecipeDAO dao = DAOFactory.getDAO(RecipeDAO.class);
                logger.info("保存处方单列表--->" + JSONUtils.toString(recipe));

                //保存处方单时检查是否已经存在该处方单
                if (dao.mpiExistRecipeByMpiAndFromFlag(recipe)) {
                    return;
                }

                Recipe r = dao.saveRecipe(recipe);
                //保存检验报告明细
                RecipeDetailDAO detailDao = DAOFactory.getDAO(RecipeDetailDAO.class);
                for (Recipedetail recipedetail : rcplist) {
                    recipedetail.setRecipeId(r.getRecipeId());
                    logger.info("保存电子处方明细--->" + JSONUtils.toString(recipedetail));
                    detailDao.saveRecipeDetail(recipedetail);
                }
                //保存电子处方文档索引
                DocIndex doc = new DocIndex();
                doc.setDocType("3");
                doc.setMpiid(r.getMpiid());
                doc.setDocClass(3);
                doc.setDocTitle(Integer.toString(r.getRecipeType()));
                doc.setDocSummary(Integer.toString(r.getRecipeType()));
                doc.setCreateOrgan(r.getClinicOrgan());
                doc.setCreateDepart(r.getDepart());
                doc.setCreateDoctor(r.getDoctor());
                doc.setCreateDate(new Date());
                doc.setGetDate(new Date());
                DocIndexDAO docIndexDAO = DAOFactory.getDAO(DocIndexDAO.class);
                logger.info("保存电子病历文档索引--->" + JSONUtils.toString(doc));
                docIndexDAO.saveDocIndex(doc);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
    }
}
