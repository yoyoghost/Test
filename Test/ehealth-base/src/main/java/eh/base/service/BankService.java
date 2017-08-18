package eh.base.service;

import ctd.persistence.DAOFactory;
import ctd.util.annotation.RpcService;
import eh.base.dao.BankDAO;
import eh.entity.base.Bank;

import java.util.List;

public class BankService {

    /**
     * 查询有效的银行信息列表，排序条件：ordernum asc
     * @return
     */
    @RpcService
    public List<Bank> findEffBanks(){
        BankDAO dao = DAOFactory.getDAO(BankDAO.class);
        return dao.findEffBanks();
    }

}
