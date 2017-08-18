package eh.util;

import ctd.persistence.DAOFactory;
import eh.base.dao.DrugListDAO;
import eh.base.dao.OrganConfigDAO;
import eh.base.dao.OrganDrugListDAO;
import eh.entity.base.DrugList;
import eh.entity.base.OrganConfig;
import eh.entity.base.OrganDrugList;
import eh.entity.cdr.Recipe;
import eh.entity.cdr.RecipeOrder;
import eh.entity.cdr.Recipedetail;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 电子处方工具类
 * Created by jiangtingfeng on 2017/6/14.
 */
public class CdrUtil
{

    /**
     * 获取处方单上药品总价
     * @param recipe
     * @param recipedetails
     */
    public static void getRecipeTotalPriceRange(Recipe recipe, List<Recipedetail> recipedetails)
    {
        List<Integer> drugIds = new ArrayList<>();
        for (Recipedetail recipedetail : recipedetails)
        {
            drugIds.add(recipedetail.getDrugId());
        }
        DrugListDAO dao = DAOFactory.getDAO(DrugListDAO.class);

        List<DrugList> drugLists = dao.findByDrugIds(drugIds);

        BigDecimal price1 = new BigDecimal(0);
        BigDecimal price2 = new BigDecimal(0);

        for (DrugList drugList : drugLists)
        {
            for (Recipedetail recipedetail : recipedetails)
            {
                if (drugList.getDrugId().equals(recipedetail.getDrugId()) && null != drugList)
                {
                    price1 = getTatolPrice(BigDecimal.valueOf(drugList.getPrice1()),recipedetail,price1);
                    price2 = getTatolPrice(BigDecimal.valueOf(drugList.getPrice2()),recipedetail,price2);
                    break;
                }
            }
        }

        recipe.setPrice1(price1.divide(BigDecimal.ONE, 2, RoundingMode.UP));
        recipe.setPrice2(price2.divide(BigDecimal.ONE, 2, RoundingMode.UP));

    }

    /**
     * 获取药品总价
     * @param price 单价
     * @param recipedetail 获取数量
     * @return
     */
    public static BigDecimal getTatolPrice(BigDecimal price,Recipedetail recipedetail,BigDecimal price1)
    {
        return price1.add(price.multiply(new BigDecimal(recipedetail.getUseTotalDose())));

    }

    /**
     * 药品获取医院价格
     * @param dList
     */
    public static void getHospitalPrice(List<DrugList> dList)
    {
        List drugIds = new ArrayList();
        for (DrugList drugList : dList)
        {
            if (null != drugList)
            {
                drugIds.add(drugList.getDrugId());
            }
        }

        OrganDrugListDAO dao = DAOFactory.getDAO(OrganDrugListDAO.class);

        List<OrganDrugList> organDrugList = dao.findByDrugId(drugIds);

        // 设置医院价格
        for (DrugList drugList : dList)
        {
            for (OrganDrugList odlist : organDrugList)
            {
                if (null != drugList && null != odlist && drugList.getDrugId().equals(odlist.getDrugId()))
                {
                    drugList.setHospitalPrice(odlist.getSalePrice());
                    break;
                }
            }
        }
    }

    /**
     * 从机构配置表中获取配置(可根据不同机构做不同配置)
     * @param order
     * @return
     */
    public static Map<String,String> getParamFromOgainConfig(RecipeOrder order)
    {
        Integer organId = order.getOrganId();
        Map<String,String> map = new HashMap<String,String>();
        if (null != organId)
        {
            OrganConfigDAO organConfigDAO = DAOFactory.getDAO(OrganConfigDAO.class);
            OrganConfig organConfig = organConfigDAO.get(organId);
            if (null != organConfig)
            {
                map.put("serviceChargeDesc",organConfig.getServiceChargeDesc());
                map.put("serviceChargeRemark",organConfig.getServiceChargeRemark());
            }
        }
        return map;
    }
}
