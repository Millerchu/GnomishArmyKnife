package com.gak.growthrecord.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.time.LocalDate;
import com.gak.growthrecord.enums.VaccinationStatus;

/** 保存GrowthVaccination的输入；用户与宝宝归属由接口路径和登录态确定。 */
public class SaveGrowthVaccinationRequest {
    @NotBlank(message = "疫苗名称不能为空")
    @Size(max = 128, message = "疫苗名称不能超过128字")
    private String vaccineName;

    @NotNull(message = "针次不能为空")
    @Min(value = 1, message = "针次必须大于0")
    @Max(value = 99, message = "针次不能超过99")
    private Integer dose;

    @NotNull(message = "接种或预约日期不能为空")
    private LocalDate vaccinationDate;

    @NotNull(message = "接种状态不能为空")
    private VaccinationStatus status;

    @Size(max = 128, message = "接种单位不能超过128字")
    private String institution;

    @Size(max = 1000, message = "备注不能超过1000字")
    private String note;

    public String getVaccineName() { return vaccineName; }
    public void setVaccineName(String vaccineName) { this.vaccineName = vaccineName; }

    public Integer getDose() { return dose; }
    public void setDose(Integer dose) { this.dose = dose; }

    public LocalDate getVaccinationDate() { return vaccinationDate; }
    public void setVaccinationDate(LocalDate vaccinationDate) { this.vaccinationDate = vaccinationDate; }

    public VaccinationStatus getStatus() { return status; }
    public void setStatus(VaccinationStatus status) { this.status = status; }

    public String getInstitution() { return institution; }
    public void setInstitution(String institution) { this.institution = institution; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}


