package com.gak.wowcharacter.service;

import com.gak.attachment.vo.AttachmentVO;
import com.gak.framework.dictionary.vo.DictionaryOptionVO;
import java.util.List;
import java.util.Map;

/** 同一批角色响应共享的字典选项和低保附件。 */
record WowCharacterViewContext(List<DictionaryOptionVO> classes, List<WowSpecOption> specs,
                               List<DictionaryOptionVO> dungeons, Map<String, String> professionLabels,
                               Map<Long, List<AttachmentVO>> vaultAttachments) {
}
