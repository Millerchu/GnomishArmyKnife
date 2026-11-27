package com.gak.healthrecord.service;

import com.gak.healthrecord.enums.HealthReportItemGroup;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 体检报告内置项目目录，用于服务端校验 itemCode 及其归属分组。
 */
public final class HealthReportItemCatalog {

    private static final Map<String, CatalogItem> ITEMS = createCatalog();

    private HealthReportItemCatalog() {
    }

    public static CatalogItem findByCode(String itemCode) {
        if (itemCode == null) {
            return null;
        }
        return ITEMS.get(itemCode);
    }

    public static Map<String, CatalogItem> all() {
        return ITEMS;
    }

    private static Map<String, CatalogItem> createCatalog() {
        Map<String, CatalogItem> map = new LinkedHashMap<>();
        // GENERAL
        put(map, HealthReportItemGroup.GENERAL, "bmiReported", "报告 BMI", "kg/m²");
        put(map, HealthReportItemGroup.GENERAL, "heightCm", "身高", "cm");
        put(map, HealthReportItemGroup.GENERAL, "weightKg", "体重", "kg");
        put(map, HealthReportItemGroup.GENERAL, "bodyFatRate", "体脂率", "%");
        put(map, HealthReportItemGroup.GENERAL, "systolicPressure", "收缩压", "mmHg");
        put(map, HealthReportItemGroup.GENERAL, "diastolicPressure", "舒张压", "mmHg");
        put(map, HealthReportItemGroup.GENERAL, "heartRate", "静息心率", "次/分");

        // LIPID
        put(map, HealthReportItemGroup.LIPID, "atherogenicIndex", "动脉粥样硬化指数", null);
        put(map, HealthReportItemGroup.LIPID, "totalCholesterol", "总胆固醇", "mmol/L");
        put(map, HealthReportItemGroup.LIPID, "triglycerides", "甘油三酯", "mmol/L");
        put(map, HealthReportItemGroup.LIPID, "hdlCholesterol", "高密度脂蛋白胆固醇", "mmol/L");
        put(map, HealthReportItemGroup.LIPID, "ldlCholesterol", "低密度脂蛋白胆固醇", "mmol/L");

        // LIVER
        put(map, HealthReportItemGroup.LIVER, "alkalinePhosphatase", "碱性磷酸酶", "U/L");
        put(map, HealthReportItemGroup.LIVER, "astAltRatio", "AST/ALT 比值", null);
        put(map, HealthReportItemGroup.LIVER, "totalBilirubin", "总胆红素", "μmol/L");
        put(map, HealthReportItemGroup.LIVER, "directBilirubin", "直接胆红素", "μmol/L");
        put(map, HealthReportItemGroup.LIVER, "indirectBilirubin", "间接胆红素", "μmol/L");
        put(map, HealthReportItemGroup.LIVER, "totalProtein", "总蛋白", "g/L");
        put(map, HealthReportItemGroup.LIVER, "albumin", "白蛋白", "g/L");
        put(map, HealthReportItemGroup.LIVER, "globulin", "球蛋白", "g/L");
        put(map, HealthReportItemGroup.LIVER, "albuminGlobulinRatio", "白球比", null);
        put(map, HealthReportItemGroup.LIVER, "alanineAminotransferase", "谷丙转氨酶", "U/L");
        put(map, HealthReportItemGroup.LIVER, "aspartateAminotransferase", "谷草转氨酶", "U/L");
        put(map, HealthReportItemGroup.LIVER, "gammaGlutamylTransferase", "γ-谷氨酰转移酶", "U/L");

        // KIDNEY
        put(map, HealthReportItemGroup.KIDNEY, "serumCreatinine", "血肌酐", "μmol/L");
        put(map, HealthReportItemGroup.KIDNEY, "urea", "尿素", "mmol/L");
        put(map, HealthReportItemGroup.KIDNEY, "ureaNitrogen", "尿素氮", "mmol/L");
        put(map, HealthReportItemGroup.KIDNEY, "egfr", "估算肾小球滤过率", null);
        put(map, HealthReportItemGroup.KIDNEY, "uricAcid", "尿酸", "μmol/L");

        // CBC
        put(map, HealthReportItemGroup.CBC, "whiteBloodCellCount", "白细胞计数", null);
        put(map, HealthReportItemGroup.CBC, "redBloodCellCount", "红细胞计数", null);
        put(map, HealthReportItemGroup.CBC, "hemoglobin", "血红蛋白", null);
        put(map, HealthReportItemGroup.CBC, "hematocrit", "红细胞压积", null);
        put(map, HealthReportItemGroup.CBC, "plateletCount", "血小板计数", null);
        put(map, HealthReportItemGroup.CBC, "plateletcrit", "血小板压积", "%");
        put(map, HealthReportItemGroup.CBC, "plateletDistributionWidth", "血小板分布宽度", null);

        // URINALYSIS
        put(map, HealthReportItemGroup.URINALYSIS, "urineSpecificGravity", "尿比重", null);
        put(map, HealthReportItemGroup.URINALYSIS, "urinePh", "尿 pH", null);
        put(map, HealthReportItemGroup.URINALYSIS, "urineProtein", "尿蛋白", null);
        put(map, HealthReportItemGroup.URINALYSIS, "urineOccultBlood", "尿隐血", null);
        put(map, HealthReportItemGroup.URINALYSIS, "urineGlucose", "尿糖", null);
        put(map, HealthReportItemGroup.URINALYSIS, "urineKetone", "尿酮体", null);
        put(map, HealthReportItemGroup.URINALYSIS, "urineLeukocyte", "尿白细胞", null);
        put(map, HealthReportItemGroup.URINALYSIS, "urineNitrite", "尿亚硝酸盐", null);
        put(map, HealthReportItemGroup.URINALYSIS, "urineSediment", "尿沉渣镜检", null);

        // IMAGING
        put(map, HealthReportItemGroup.IMAGING, "fattyLiverGrade", "脂肪肝分级", null);
        put(map, HealthReportItemGroup.IMAGING, "liverCyst", "肝囊肿", null);
        put(map, HealthReportItemGroup.IMAGING, "gallbladderPolyp", "胆囊息肉", null);
        put(map, HealthReportItemGroup.IMAGING, "renalCyst", "肾囊肿", null);
        put(map, HealthReportItemGroup.IMAGING, "thyroidTiRads", "甲状腺 TI-RADS", null);
        put(map, HealthReportItemGroup.IMAGING, "prostateFinding", "前列腺超声所见", null);
        put(map, HealthReportItemGroup.IMAGING, "chestImaging", "胸部影像", null);
        put(map, HealthReportItemGroup.IMAGING, "cervicalSpineImaging", "颈椎影像", null);
        put(map, HealthReportItemGroup.IMAGING, "echocardiogram", "心脏超声", null);

        // ECG
        put(map, HealthReportItemGroup.ECG, "ecgConclusion", "心电图结论", null);
        put(map, HealthReportItemGroup.ECG, "ecgPr", "PR 间期", "ms");
        put(map, HealthReportItemGroup.ECG, "ecgQrs", "QRS 时限", "ms");
        put(map, HealthReportItemGroup.ECG, "ecgQt", "QT 间期", "ms");
        put(map, HealthReportItemGroup.ECG, "ecgQtc", "QTc", "ms");
        put(map, HealthReportItemGroup.ECG, "ecgAxis", "电轴", "°");

        // SPECIALTY
        put(map, HealthReportItemGroup.SPECIALTY, "visualAcuity", "视力", null);
        put(map, HealthReportItemGroup.SPECIALTY, "oralFinding", "口腔所见", null);
        put(map, HealthReportItemGroup.SPECIALTY, "entFinding", "耳鼻喉所见", null);
        put(map, HealthReportItemGroup.SPECIALTY, "afp", "甲胎蛋白", null);
        put(map, HealthReportItemGroup.SPECIALTY, "cea", "癌胚抗原", null);
        put(map, HealthReportItemGroup.SPECIALTY, "hbsAg", "乙肝表面抗原", null);
        put(map, HealthReportItemGroup.SPECIALTY, "fastingGlucose", "空腹血糖", "mmol/L");

        return Collections.unmodifiableMap(map);
    }

    private static void put(Map<String, CatalogItem> map, HealthReportItemGroup group,
                            String code, String name, String defaultUnit) {
        map.put(code, new CatalogItem(group, code, name, defaultUnit));
    }

    public record CatalogItem(HealthReportItemGroup group, String code, String name, String defaultUnit) {
    }
}
