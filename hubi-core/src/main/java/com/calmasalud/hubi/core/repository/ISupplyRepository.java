package com.calmasalud.hubi.core.repository;
import com.calmasalud.hubi.core.model.Supply;
import java.util.List;

public interface ISupplyRepository {
    void add(Supply supply);
    void modify(Supply supply);
    void delete(long id);
    Supply findByID(long id);
    List<Supply> listAll();
    String getNextCorrelativeCode(String colorName, String tipoFilamento);
}