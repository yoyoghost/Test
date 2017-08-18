package eh.base.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.annotation.RpcService;
import eh.entity.base.Organ;
import eh.entity.base.OrganConfig;

import java.math.BigDecimal;
import java.util.List;

public abstract class OrganConfigDAO extends HibernateSupportDelegateDAO<OrganConfig> {

    public OrganConfigDAO() {
        super();
        this.setEntityName(OrganConfig.class.getName());
        this.setKeyField("organId");

    }

    @DAOMethod
    public abstract OrganConfig getByOrganId(Integer organId);

    @DAOMethod(sql = "select organId from OrganConfig where accountFlag=:accountFlag" ,limit = 0)
    public abstract List<Integer> findOrganIdByAccountFlag(@DAOParam("accountFlag") Integer accountFlag);

    @DAOMethod(sql="select o from Organ o,OrganConfig oc where o.organId=oc.organId and oc.diagnosisCenterFlag=1")
    public abstract List<Organ> findAllDiagnosisCenter();

    @DAOMethod(sql="select priceForRecipeRegister from OrganConfig where organId=:organId")
    public abstract BigDecimal getRecipeRegisterByOrganId(@DAOParam("organId") Integer organId);

    @DAOMethod(sql="select recipeDecoctionPrice from OrganConfig where organId=:organId")
    public abstract BigDecimal getDecoctionPriceByOrganId(@DAOParam("organId") Integer organId);

    @DAOMethod(sql="select recipeCreamPrice from OrganConfig where organId=:organId")
    public abstract BigDecimal getRecipeCreamPriceByOrganId(@DAOParam("organId") Integer organId);

    @DAOMethod(sql="select organId from OrganConfig where enable_drug_sync=1" ,limit=0)
    public abstract List<Integer> findOrganIdsByEnableDrugSync();

    @RpcService
    @DAOMethod(sql = "update OrganConfig set recipeDecoctionPrice=:decoctionPrice where organId=:organId")
    public abstract void updateDecoctionPriceByOrganId(@DAOParam("organId") Integer organId,
                                                          @DAOParam("decoctionPrice") BigDecimal decoctionPrice);

    @DAOMethod(sql="select enableSecondsign from OrganConfig where organId=:organId" ,limit=1)
    public abstract Boolean getEnableSecondsignByOrganId(@DAOParam("organId") Integer organId);

    @DAOMethod(sql="select remindMobile from OrganConfig where organId=:organId" ,limit=1)
    public abstract String getRemindMobileByOrganId(@DAOParam("organId") Integer organId);


    @DAOMethod(sql = "select appointCloudPayDay from OrganConfig where organId=:organId")
    public abstract Integer getAppointCloudPayDayByOrganId(@DAOParam("organId") Integer organId);

    @DAOMethod(sql = "select teachUrl from OrganConfig where organId=:organId")
    public abstract String getTeachUrlByOrganId(@DAOParam("organId") Integer organId);
}
