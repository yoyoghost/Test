package eh.bus.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.annotation.RpcService;
import eh.entity.bus.Evaluate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Created by Aliang on 2017/6/2.
 */
public abstract class EvaluateDAO extends HibernateSupportDelegateDAO<Evaluate> {
    public static final Logger logger = LoggerFactory.getLogger(EvaluateDAO.class);

    public EvaluateDAO(){
        super();
        this.setEntityName(Evaluate.class.getName());
        this.setKeyField("id");
    }

    /**
     * 根据业务类型和id查询评价
     * @param bussType
     * @param bussId
     * @return
     */
    @RpcService
    @DAOMethod()
    public abstract List<Evaluate> findEvaluateByBussTypeAndBussId(int bussType,int bussId);

    /**
     * 保存评价列表
     * @param evaluates
     */
    public Boolean addEvaluate(List<Evaluate> evaluates){
        for(Evaluate e:evaluates){
            if(save(e).getId()==null){
                throw new DAOException("addEvaluate Failed for bussType["+e.getBussType()+"],bussId["+e.getBussId()+"]");
            }
        }
        return true;
    }

    /**
     * 根据业务类型和id判断是否已评价
     * @param bussType
     * @param bussId
     * @return
     */
    @RpcService
    public Boolean isEvaluate(int bussType,int bussId){
        List<Evaluate> list=findEvaluateByBussTypeAndBussId(bussType,bussId);
        if(list!=null&&!list.isEmpty()){
            return true;
        }
        return false;
    }
}
