package eh.mpi.service.healthcard;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.constant.SystemConstant;
import eh.entity.mpi.HealthCard;
import eh.mpi.dao.HealthCardDAO;
import eh.utils.params.ParamUtils;
import eh.utils.params.ParameterConstant;
import org.apache.commons.lang3.StringUtils;

/**
 * Created by luf on 2016/6/29.
 */

public class QueryHealthCardService {

    /**
     * 判断医保卡号是否被注册
     *
     * @param mpiId     修改人主索引
     * @param cardOrgan 发卡机构
     * @param cardId    医保卡号
     * @return
     * @author luf
     */
    @RpcService
    public Boolean isUsedCard(String mpiId, int cardOrgan, String cardId) {
        if (StringUtils.isEmpty(mpiId)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "mpiId is required!");
        }
        if (StringUtils.isEmpty(cardId)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "cardId is required!");
        }
        if (cardOrgan <= 0) {
            throw new DAOException(DAOException.VALUE_NEEDED, "cardOrgan is required!");
        }

        HealthCardDAO cardDAO = DAOFactory.getDAO(HealthCardDAO.class);
        HealthCard orgCard = cardDAO.getByCardOrganAndCardId(
                cardOrgan, cardId.toUpperCase(), cardId);
        if (orgCard != null && !StringUtils.isEmpty(orgCard.getMpiId())) {
            if (!orgCard.getMpiId().equals(mpiId)) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "该医保卡号已被使用。如有问题，请联系纳里客服：\n" + ParamUtils.getParam(ParameterConstant.KEY_CUSTOMER_TEL,SystemConstant.CUSTOMER_TEL));
            }
        }
        return true;
    }

    @RpcService
    public Boolean isUsedCardByNewPatient(int cardOrgan, String cardId) {
        if (StringUtils.isEmpty(cardId)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "cardId is required!");
        }
        if (cardOrgan <= 0) {
            throw new DAOException(DAOException.VALUE_NEEDED, "cardOrgan is required!");
        }

        HealthCardDAO cardDAO = DAOFactory.getDAO(HealthCardDAO.class);
        HealthCard orgCard = cardDAO.getByCardOrganAndCardId(
                cardOrgan, cardId.toUpperCase(), cardId);
        if (orgCard != null && !StringUtils.isEmpty(orgCard.getMpiId())) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该医保卡号已被使用。如有问题，请联系纳里客服：\n" + ParamUtils.getParam(ParameterConstant.KEY_CUSTOMER_TEL,SystemConstant.CUSTOMER_TEL));
        }
        return true;
    }
}
