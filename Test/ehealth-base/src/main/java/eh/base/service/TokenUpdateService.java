package eh.base.service;

import ctd.util.AppContextHolder;
import eh.cdr.service.RecipeService;

/**
 * company: ngarihealth
 * author: 0184/yu_yun
 * date:2016/8/24.
 */
public class TokenUpdateService {

    /**
     * tomcat启动时调用
     */
    public void updateTokenAfterInit(){
        RecipeService recipeService = AppContextHolder.getBean("recipeService", RecipeService.class);
        recipeService.updateDrugsEnterpriseToken();
    }
}
