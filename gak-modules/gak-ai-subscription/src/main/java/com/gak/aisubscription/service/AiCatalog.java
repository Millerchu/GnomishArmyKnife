package com.gak.aisubscription.service;
import com.gak.aisubscription.vo.AiToolVO;
import java.util.List;
/** 预置工具不包含未经核验价格或自动同步承诺。 */
public final class AiCatalog {
 private AiCatalog() {}
 public static final List<AiToolVO> TOOLS=List.of(
  new AiToolVO("CHATGPT","ChatGPT",true,"手动记录 · OpenAI 组织 API 费用同步"),
  new AiToolVO("GROK","Grok",true,"手动记录 · xAI 团队 API 费用同步"),
  tool("GEMINI","Gemini"),tool("MUSE","Muse"),tool("QODERCN","QoderCN"),tool("WORKBUDDY","Workbuddy"));
 private static AiToolVO tool(String code,String name){return new AiToolVO(code,name,false,"支持手动记录；消费者订阅用量未接入自动同步");}
 public static void requireTool(String code){AiRules.check(TOOLS.stream().anyMatch(t->t.code().equals(code)),"请选择预置 AI 工具");}
}
