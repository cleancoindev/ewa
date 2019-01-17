package com.credits.wallet.desktop.controller;

import com.credits.general.exception.CreditsException;
import com.credits.general.util.Callback;
import com.credits.wallet.desktop.VistaNavigator;
import com.credits.wallet.desktop.utils.FormUtils;
import com.credits.wallet.desktop.utils.SmartContractsUtils;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.credits.wallet.desktop.AppState.coinsKeeper;
import static com.credits.wallet.desktop.AppState.contractInteractionService;

/**
 * Created by goncharov-eg on 07.02.2018.
 */
public class NewCoinController implements FormInitializable {
    private final static Logger LOGGER = LoggerFactory.getLogger(NewCoinController.class);

    private static final String ERR_COIN = "You must enter coin mnemonic";
    private static final String ERR_TOKEN = "You must enter token";
    private static final String ERR_COIN_DUPLICATE = "Coin already exists";

    @FXML
    private TextField tokenField;
    @FXML
    private TextField coinField;
    @FXML
    private Label tokenErrorLabel;
    @FXML
    private Label coinErrorLabel;

    @FXML
    private void handleBack() {
        VistaNavigator.loadVista(VistaNavigator.WALLET,this);
    }

    @FXML
    private void handleSave(){
        clearFormErrors();

        String coinName = coinField.getText().replace(";", "");
        String smartContractAddress = tokenField.getText().replace(";", "");

        if (checkValidData(coinName, smartContractAddress)) {
            addSmartContractTokenBalance(coinName, smartContractAddress);
            VistaNavigator.loadVista(VistaNavigator.WALLET,this);
        }
    }

    private boolean checkValidData(String coinName, String smartContractAddress) {
        AtomicBoolean isValidationSuccessful = new AtomicBoolean(true);
        if (coinName.isEmpty()) {
            FormUtils.validateField(coinField, coinErrorLabel, ERR_COIN, isValidationSuccessful);
        }

        if (smartContractAddress.isEmpty()) {
            FormUtils.validateField(tokenField, tokenErrorLabel, ERR_TOKEN, isValidationSuccessful);
        }

        coinsKeeper.getKeptObject().ifPresent(coinsMap -> {
            if(coinsMap.containsKey(coinName)) {
                FormUtils.validateField(coinField, coinErrorLabel, ERR_COIN_DUPLICATE, isValidationSuccessful);
            }
        });

        return isValidationSuccessful.get();
    }

    @Override
    public void initializeForm(Map<String, Object> objects) {
        clearFormErrors();
    }

    private void clearFormErrors() {
        FormUtils.clearErrorOnField(tokenField, tokenErrorLabel);
        FormUtils.clearErrorOnField(coinField, coinErrorLabel);
    }

    public static void addSmartContractTokenBalance(String coinName, String smartContractAddress) {
        contractInteractionService.getSmartContractBalance(smartContractAddress, new Callback<BigDecimal>() {
            @Override
            public void onSuccess(BigDecimal balance) throws CreditsException {
                SmartContractsUtils.saveSmartInTokenList(coinName, balance, smartContractAddress);
                if(balance != null){
                    FormUtils.showPlatformInfo("Coin \"" + coinName + "\" was created successfully");
                }
            }

            @Override
            public void onError(Throwable e) {
                FormUtils.showError("Coin can't created. Reason: " + e.getMessage());
            }
        });
    }

}
