package eh.base.dao;

import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.impl.dictionary.DBDictionaryItemLoader;
import ctd.util.annotation.RpcService;
import eh.entity.base.BussDescription;
import eh.entity.base.OrganBusAdvertisement;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;

/**
 * Created by hwg on 2016/10/20.
 */
public abstract class BussDescriptionDAO extends HibernateSupportDelegateDAO<BussDescription>implements DBDictionaryItemLoader<BussDescription>{

    private static final Log logger = LogFactory.getLog(BussDescriptionDAO.class);

    public BussDescriptionDAO(){
        super();
        this.setEntityName(BussDescriptionDAO.class.getName());
        this.setKeyField("bussdescId");
    }

    /**
     * 查询医院个性化描述
     * @param organId 机构编码
     * @param description 医院个性化描述
     * @return
     */
    @RpcService
    @DAOMethod(sql ="from BussDescription where organId =:organId and description =:description")
    public abstract BussDescription getByOrganIdAndDescription(@DAOParam("organId")int organId, @DAOParam("description") String description);

    @RpcService
    @DAOMethod(sql = "from BussDescription")
    public abstract List<BussDescription> findByAll();

    @RpcService
    @DAOMethod(sql = "from BussDescription where organId =:organId")
    public abstract BussDescription getByOrganId(@DAOParam("organId") int organId);


    /**
     * 医院个性化服务app端
     * @param organId
     * @param busType
     * @return
     */
    @RpcService
    public String queryDescription(Integer organId,Integer busType) {
        if (organId != null && busType != null) {
            OrganBusAdvertisementDAO dao = DAOFactory.getDAO(OrganBusAdvertisementDAO.class);
            OrganBusAdvertisement oba = dao.getByOrganIdAndBusType(organId, busType);
            if (oba == null){
                logger.error("没有返回数据");
                return null;
            }
            String advertisement = oba.getAdvertisement();
            if (advertisement == null){
                logger.error("该医院没有预约说明");
            }
            return advertisement;
        }
        return null;
    }

    @RpcService
    public List<OrganBusAdvertisement> queryDescriptionList(){

            OrganBusAdvertisementDAO dao = DAOFactory.getDAO(OrganBusAdvertisementDAO.class);
            List<OrganBusAdvertisement> list = dao.findAll();
            if (list.size() > 0){
//                logger.info("返回list数据给前端");
                return list;
            }else{
                logger.error("没有返回数据");
                return null;
            }
    }

    @RpcService
    @DAOMethod(sql = "from BussDescription where organId =:organId and features =:features")
    public abstract BussDescription getByOrganIdAndFeatures(@DAOParam("organId") int organId,@DAOParam("features") Integer features);

}
