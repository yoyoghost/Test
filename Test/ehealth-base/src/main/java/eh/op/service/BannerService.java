package eh.op.service;

import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.util.annotation.RpcService;
import eh.base.dao.DoctorDAO;
import eh.entity.msg.Banner;
import eh.entity.msg.BannerAndOrgans;
import eh.entity.msg.BannerTarget;
import eh.op.dao.BannerDAO;
import eh.op.dao.BannerTargetDAO;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by houxr on 2016/6/22.
 * banner相关服务
 */
public class BannerService {

    private static final Logger logger = Logger.getLogger(BannerService.class);

    /**
     * 新建Banner|加载页
     *
     * @param banner
     * @param organs
     * @return
     * @author houxr
     * @date 2016-06-21 10:22:23
     */
    @RpcService
    public Integer createBannerAndBannerTarget(Banner banner, List<String> organs) {
        BannerDAO bannerDAO = DAOFactory.getDAO(BannerDAO.class);
        return bannerDAO.createBannerAndOrgans(banner, organs);
    }

    /**
     * 根据机构、banner状态查询banner列表
     *
     * @param banner
     * @param organs
     * @return
     * @author houxr
     * @date 2016-06-21 10:22:23
     */
    @RpcService
    public Boolean updateBannerAndOrgans(Banner banner, List<String> organs) {
        BannerDAO bannerDAO = DAOFactory.getDAO(BannerDAO.class);
        return bannerDAO.updateBannerAndOrgans(banner, organs);
    }


    @RpcService
    public QueryResult<BannerAndOrgans> queryBannerByOrganIdAndStatusAndType(final List<String> organs,
                                                                             final Integer status,
                                                                             final String bannerType,
                                                                             final int start,
                                                                             final int limit) {
        BannerDAO bannerDAO = DAOFactory.getDAO(BannerDAO.class);
        return bannerDAO.queryBannerByOrganIdAndStatusAndType(organs, status, bannerType, start, limit);
    }


    @RpcService
    public BannerAndOrgans findBannerByBannerId(final Integer bannerId) {
        BannerDAO bannerDAO = DAOFactory.getDAO(BannerDAO.class);
        Banner banner = bannerDAO.get(bannerId);
        BannerTargetDAO bannerTargetDao = DAOFactory.getDAO(BannerTargetDAO.class);
        List<BannerTarget> bannerTargetList = bannerTargetDao.findBannerTargetByBannerId(banner.getBannerId());
        return new BannerAndOrgans(banner, bannerTargetList);
    }

    /**
     * 根据医生内码查询banner或加载页
     *
     * @param doctorId
     * @param bannerType
     * @return
     */
    @RpcService
    public List<Banner> findBannerByDoctorId(final Integer doctorId, final String bannerType) {
        BannerDAO bannerDAO = DAOFactory.getDAO(BannerDAO.class);
        List<Banner> bannerList = new ArrayList<Banner>();
        if (StringUtils.equals("0", bannerType)) {//加载页 返回一个banner 并且是status=1在用
            List<Banner> bannerListOld = bannerDAO.findBannerByDoctorIdAndTypeAndSta(doctorId, bannerType);
            if (bannerListOld.size() > 0) {
                bannerList.add(bannerListOld.get(0));
            }
        } else {
            bannerList = bannerDAO.findBannerByDoctorIdAndType(doctorId, bannerType, 1);
        }
        return bannerList;
    }

    @RpcService
    public List<BannerTarget> findBannerTargetByOrganId(final Integer doctorId) {
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Integer organId = doctorDAO.getByDoctorId(doctorId).getOrgan();
        BannerTargetDAO bannerTargetDAO = DAOFactory.getDAO(BannerTargetDAO.class);
        return bannerTargetDAO.findByOrganId(organId);
    }

    /**
     * 根据bannerId删除banner
     *
     * @param bannerId
     * @return
     */
    @RpcService
    public Banner deleteBannerByBannerId(Integer bannerId) {
        BannerDAO bannerDAO = DAOFactory.getDAO(BannerDAO.class);
        Banner banner = bannerDAO.get(bannerId);
        banner.setStatus(9);
        return bannerDAO.update(banner);
    }

    @RpcService
    public List<Banner> findBannerByPlatform(String configId,String bannerType,Integer status){
        BannerDAO bannerDAO = DAOFactory.getDAO(BannerDAO.class);
        return bannerDAO.findBannerByPlatform(configId,bannerType,status);
    }


}
