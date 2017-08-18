package eh.op.service;

import com.alibaba.druid.util.StringUtils;
import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.util.annotation.RpcService;
import eh.base.service.BusActionLogService;
import eh.entity.mindgift.Gift;
import eh.entity.wx.WXConfig;
import eh.mindgift.dao.GiftDAO;
import eh.mindgift.dao.MindGiftDAO;
import eh.op.dao.WxAppPropsDAO;

import java.util.Date;
import java.util.List;

/**
 * 心意运营平台服务
 *
 * @author jianghc
 * @create 2017-03-24 10:22
 **/
public class MindGiftOpService {
    private GiftDAO giftDAO;
    private MindGiftDAO mindGiftDAO;
    private WxAppPropsDAO wxAppPropsDAO;

    public MindGiftOpService() {
        this.giftDAO = DAOFactory.getDAO(GiftDAO.class);
        this.mindGiftDAO = DAOFactory.getDAO(MindGiftDAO.class);
        this.wxAppPropsDAO = DAOFactory.getDAO(WxAppPropsDAO.class);
    }


    @RpcService
    public List<Gift> findGift(int start, int limit) {
        return giftDAO.findALLGiftsByLimit(start, limit);
    }

    @RpcService
    public QueryResult<Gift> queryGiftByGiftTypeAndGiftStatus(Integer giftType, Integer giftStatus, final int start, final int limit) {
        return giftDAO.queryGiftByGiftTypeAndGiftStatus(giftType, giftStatus, start, limit);
    }

    @RpcService
    public Gift saveOneGift(Gift gift) {
        if (gift == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "gift is require");
        }
        if (gift.getGiftName() == null || StringUtils.isEmpty(gift.getGiftName().trim())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "giftName is require");
        }
        if (gift.getGiftIcon() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "giftIcon is require");
        }
        if (gift.getGiftType() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "giftType is require");
        }
        if (gift.getVirtualFlag() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "virtualFlag is require");
        }
        gift.setGiftPrice(gift.getGiftPrice() == null ? Double.valueOf(0.0) : gift.getGiftPrice());
        gift.setGiftStatus(1);
        gift = giftDAO.save(gift);
        BusActionLogService.recordBusinessLog("礼品管理", gift.getGiftId().toString(), "Gift", "新增礼品：" + gift.toString());
        return gift;
    }

    @RpcService
    public void removeOneGift(Integer giftId) {
        if (giftId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "giftId is require");
        }
        Gift gift = giftDAO.get(giftId);
        if (gift == null) {
            throw new DAOException("giftId is not exist");
        }
        gift.setGiftStatus(0);
        gift = giftDAO.update(gift);
        BusActionLogService.recordBusinessLog("礼品管理", gift.getGiftId().toString(), "Gift", "删除礼品：" + gift.toString());
    }

    @RpcService
    public QueryResult<Object> queryMindGiftByStartAndLimit(Date bDate, Date eDate, String mpiId, Integer doctorId,
                                                                Integer department, Integer organ, Integer busType,
                                                                Integer subBusType, Integer giftId, Integer payflag, int start, int limit) {
        return mindGiftDAO.queryMindGiftByStartAndLimit(bDate, eDate, mpiId, doctorId, department, organ, busType, subBusType, giftId, payflag, start, limit);
    }


    @RpcService
    public QueryResult<WXConfig> queryWxConfigForMindGift(Boolean canMindGift,int start,int limit){
        canMindGift = canMindGift == null ? Boolean.FALSE : canMindGift;
        return wxAppPropsDAO.queryWxConfigForMindGift(canMindGift,start,limit);
    }
}
