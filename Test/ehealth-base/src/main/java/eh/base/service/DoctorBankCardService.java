package eh.base.service;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.BeanUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.dao.BankDAO;
import eh.base.dao.DoctorAccountDetailDAO;
import eh.base.dao.DoctorBankCardDAO;
import eh.base.dao.DoctorDAO;
import eh.bus.constant.DoctorBankCardConstant;
import eh.entity.base.DoctorAccountDetail;
import eh.entity.base.DoctorBankCard;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class DoctorBankCardService {

    /**
     * 删除银行卡信息
     * @param card
     */
    @RpcService
    public Boolean delBankCard(Integer bankCardId){

        DoctorBankCardDAO dao = DAOFactory.getDAO(DoctorBankCardDAO.class);
        if(bankCardId==null || !dao.exist(bankCardId)){
            throw new DAOException(DAOException.VALUE_NEEDED,"bankCardId is needed or bankCard["+bankCardId+"] is not found");
        }
        DoctorBankCard target=dao.get(bankCardId);
        target.setStatus(DoctorBankCardConstant.BANK_CARD_STATUS_NOEFF);
        dao.update(target);
        return true;
    }

    /**
     * 更新银行卡信息
     * @param card
     */
    @RpcService
    public DoctorBankCard updateBankCard(DoctorBankCard card){
        BankDAO bankdao = DAOFactory.getDAO(BankDAO.class);
        DoctorBankCardDAO dao = DAOFactory.getDAO(DoctorBankCardDAO.class);

        Integer bankCardId=card.getId();
        if(bankCardId==null || !dao.exist(bankCardId)){
            throw new DAOException(DAOException.VALUE_NEEDED,"bankCardId is needed or bankCard["+bankCardId+"] is not found");
        }
        //以下字段不给予更新
        card.setCreateDate(null);
        card.setDoctorId(null);
        card.setStatus(null);

        DoctorBankCard target=dao.get(bankCardId);
        target.setLastModify(new Date());
        BeanUtils.map(card, target);
        DoctorBankCard dbc = dao.update(target);

        dbc.setBankIcon(bankdao.getBankIconByBankName(dbc.getBankName()));
        return dbc;
    }

    /**
     * 绑定银行卡
     * @param card
     */
    @RpcService
    public void addBankCard(DoctorBankCard card){
        DoctorBankCardDAO dao = DAOFactory.getDAO(DoctorBankCardDAO.class);

        card=isValidBankCardData(card);
        List<DoctorBankCard> list=dao.findBankCards(card.getDoctorId());
        if(list.size()>= DoctorBankCardConstant.BANK_CARD_NUM){
            throw new DAOException(ErrorCode.SERVICE_ERROR,"您已绑定"+DoctorBankCardConstant.BANK_CARD_NUM+"张银行卡，不能再继续绑定");
        }

        List<DoctorBankCard> tarCards=dao.findByCardNo(card.getCardNo());

        if(tarCards.size()>0){
            throw new DAOException(ErrorCode.SERVICE_ERROR,"该银行卡已经被绑定了，您不能再绑定该银行卡");
        }
        card.setStatus(DoctorBankCardConstant.BANK_CARD_STATUS_EFF);
        card.setCreateDate(new Date());
        card.setLastModify(new Date());
        dao.save(card);
    }

    /**
     * 获取所有绑定的有效银行卡信息
     * @return
     */
    @RpcService
    public List<DoctorBankCard> findBankCards(Integer doctorId){
        DoctorBankCardDAO dao = DAOFactory.getDAO(DoctorBankCardDAO.class);
        isValidDoctorData(doctorId);

        List<DoctorBankCard> list=dao.findBankCards(doctorId);

        return getNewBankCards(list);
    }

    /**
     * 获取默认绑定银行卡
     * 1、有提现记录且上次提现银行卡未改变卡号：默认上次提现的银行卡；
     * 2、有提现记录且上次提现银行卡不存在(删除/修改了卡号):默认最新修改的银行卡(包含最新新增的银行卡)
     * 3、无提现记录：默认最新修改的银行卡(包含最新新增的银行卡)
     * @param doctorId
     * @return
     */
    @RpcService
    public DoctorBankCard getLastUseCard(Integer doctorId){
        DoctorBankCardDAO bankCardDao = DAOFactory.getDAO(DoctorBankCardDAO.class);
        BankDAO bankdao = DAOFactory.getDAO(BankDAO.class);
        DoctorAccountDetailDAO detailDAO=DAOFactory.getDAO(DoctorAccountDetailDAO.class);

        isValidDoctorData(doctorId);

        Boolean hasWithdraw=true;
        List<DoctorAccountDetail> details=detailDAO.findLastOutDetail(doctorId);
        DoctorAccountDetail detail=null;

        if(details.size()>0){
            detail=details.get(0);
        }

        if(detail==null ){
            hasWithdraw=false;
        }else{
            Integer lastCardId=detail.getBankCardId();
            String detailCardNo=StringUtils.isEmpty(detail.getCardNo())?"":detail.getCardNo().trim();

            DoctorBankCard newCard=bankCardDao.get(lastCardId);
            //绑定的银行卡无效(前端删除) or 上次提现的银行卡号与相关联的绑定银行卡卡号不相同
            if(newCard==null || newCard.getStatus()==null || newCard.getStatus()!=1 ||
                    StringUtils.isEmpty(newCard.getCardNo().trim())|| !newCard.getCardNo().trim().equals(detailCardNo)){
                hasWithdraw=false;
            }
        }

        DoctorBankCard card=null;
        //查询到上次提现的银行卡信息
        if(hasWithdraw){
            card=new DoctorBankCard();
            card.setId(detail.getBankCardId());
            card.setBankName(detail.getBankName());
            card.setCardName(detail.getCardName());
            card.setCardNo(detail.getCardNo());
            card.setSubBank(detail.getSubBank());
            card.setBankIcon(bankdao.getBankIconByBankName(detail.getBankName()));
        }else{
            List<DoctorBankCard> cards=bankCardDao.findLastModifyBankCard(doctorId);
            if(cards.size()>0){
                card=cards.get(0);
                card.setBankIcon(bankdao.getBankIconByBankName(card.getBankName()));
            }
        }

        return card;
    }

    private DoctorBankCard isValidBankCardData(DoctorBankCard card){
        DoctorDAO docDao = DAOFactory.getDAO(DoctorDAO.class);

        Integer doctorId=card.getDoctorId();
        if(doctorId==null || !docDao.exist(doctorId)){
            throw new DAOException(DAOException.VALUE_NEEDED,"doctorId is needed");
        }

        String cardNo=card.getCardNo();
        if(StringUtils.isEmpty(cardNo)){
            throw new DAOException(DAOException.VALUE_NEEDED,"cardNo is needed");
        }
        card.setCardNo(cardNo.trim());

        String bankName=card.getBankName();
        if(StringUtils.isEmpty(bankName)){
            throw new DAOException(DAOException.VALUE_NEEDED,"bankName is needed");
        }
        card.setBankName(bankName.trim());

        String cardName=card.getCardName();
        if(StringUtils.isEmpty(cardName)){
            throw new DAOException(DAOException.VALUE_NEEDED,"cardName is needed");
        }
        card.setCardName(cardName.trim());

        String subBank=card.getSubBank();
        if(StringUtils.isEmpty(subBank)){
            throw new DAOException(DAOException.VALUE_NEEDED,"subBank is needed");
        }
        card.setSubBank(subBank.trim());
        return card;
    }

    private void isValidDoctorData(Integer doctorId){
        DoctorDAO docDao = DAOFactory.getDAO(DoctorDAO.class);

        if(doctorId==null || !docDao.exist(doctorId)){
            throw new DAOException(DAOException.VALUE_NEEDED,"doctorId is needed");
        }
    }

    /**
     * 循环填充银行图标
     * @param list
     * @return
     */
    private List<DoctorBankCard> getNewBankCards(List<DoctorBankCard> list){
        BankDAO bankdao = DAOFactory.getDAO(BankDAO.class);
        List<DoctorBankCard> returnList=new ArrayList<DoctorBankCard>();
        for (DoctorBankCard card:list) {
            card.setBankIcon(bankdao.getBankIconByBankName(card.getBankName()));
            returnList.add(card);
        }

        return returnList;
    }
}
