package eh.base.dao;/**
 * Created by Administrator on 2017-07-07.
 */

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.annotation.RpcService;
import eh.entity.base.DepartmentRelation;
import org.apache.log4j.Logger;

import java.util.List;

/**
 * @author zhuangyq
 * @create 2017-07-07 下午 14:18
 **/
public abstract class DepartmentRelationDAO extends
        HibernateSupportDelegateDAO<DepartmentRelation> {

    public static final Logger log = Logger.getLogger(DepartmentRelationDAO.class);

    public DepartmentRelationDAO() {
        super();
        this.setEntityName(DepartmentRelationDAO.class.getName());
        this.setKeyField("id");
    }
    @RpcService
    @DAOMethod(sql = "from DepartmentRelation where organId=:organId",limit = 0)
    public abstract List<DepartmentRelation> findDepartmentRelationByOrganId(@DAOParam("organId")Integer organId);
}
