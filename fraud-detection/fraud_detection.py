"""Fraud detection : binary classification on bank_transactions.csv.

Usage : python fraud_detection.py [path/to/bank_transactions.csv]
Protocol : everything (preprocessing, model choice, decision threshold) is decided on the training set
through cross-validation. The test set is only read once, at the very end, for the final evaluation.
"""
import json
import sys
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
from sklearn.compose import ColumnTransformer
from sklearn.ensemble import RandomForestClassifier
from sklearn.impute import SimpleImputer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (average_precision_score, confusion_matrix, f1_score, precision_recall_curve,
                             precision_score, recall_score)
from sklearn.model_selection import StratifiedKFold, cross_val_predict, train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder, StandardScaler

from generate_synthetic_dataset import generate

SEED = 42
HERE = Path(__file__).parent
RESULTS = HERE / "results"
TARGET = "is_fraud"
IDENTIFIERS = ["transaction_id", "account_id"]
NUMERIC = ["amount", "hour", "transactions_last_24h", "average_amount_last_7d"]
CATEGORICAL = ["country", "merchant_category"]
# Business assumption used to pick the threshold : recall matters but alerts have a cost
MIN_PRECISION = 0.30


def load_data(path):
    if path.exists():
        return pd.read_csv(path), False
    print(f"WARNING : {path} not found, a SYNTHETIC dataset is generated and saved there")
    frame = generate()
    frame.to_csv(path, index=False)
    return frame, True


def build_preprocessing():
    # Inside the pipeline, so each fit only ever sees training data (no leakage, even in cross-validation)
    numeric = Pipeline([("impute", SimpleImputer(strategy="median")), ("scale", StandardScaler())])
    categorical = Pipeline([("impute", SimpleImputer(strategy="most_frequent")),
                            ("encode", OneHotEncoder(handle_unknown="ignore"))])
    return ColumnTransformer([("num", numeric, NUMERIC), ("cat", categorical, CATEGORICAL)])


def build_models():
    return {
        "logistic_regression": Pipeline([("prep", build_preprocessing()),
                                         ("model", LogisticRegression(class_weight="balanced", max_iter=1000))]),
        "random_forest": Pipeline([("prep", build_preprocessing()),
                                   ("model", RandomForestClassifier(n_estimators=200, min_samples_leaf=5,
                                                                    class_weight="balanced", n_jobs=-1, random_state=SEED))]),
    }


def choose_threshold(y_true, scores):
    """Best F1 among the thresholds reaching MIN_PRECISION, computed on out-of-fold training predictions."""
    precision, recall, thresholds = precision_recall_curve(y_true, scores)
    precision, recall = precision[:-1], recall[:-1]
    f1 = 2 * precision * recall / np.maximum(precision + recall, 1e-12)
    acceptable = precision >= MIN_PRECISION
    candidates = np.where(acceptable, f1, -1)
    return float(thresholds[int(np.argmax(candidates))])


def evaluate(y_true, scores, threshold):
    predictions = (scores >= threshold).astype(int)
    tn, fp, fn, tp = confusion_matrix(y_true, predictions, labels=[0, 1]).ravel()
    return {
        "threshold": round(threshold, 4),
        "precision_fraud": round(precision_score(y_true, predictions, zero_division=0), 4),
        "recall_fraud": round(recall_score(y_true, predictions), 4),
        "f1_fraud": round(f1_score(y_true, predictions), 4),
        "average_precision": round(average_precision_score(y_true, scores), 4),
        "accuracy": round(float((predictions == y_true).mean()), 4),
        "confusion_matrix": {"tn": int(tn), "fp": int(fp), "fn": int(fn), "tp": int(tp)},
    }


def save_plots(y_test, scores, metrics):
    precision, recall, _ = precision_recall_curve(y_test, scores)
    figure, axes = plt.subplots(1, 2, figsize=(10, 4))
    axes[0].plot(recall, precision, color="#1f6feb")
    axes[0].axhline(y_test.mean(), color="#888888", linestyle="--", label=f"Hasard (taux de fraude = {y_test.mean():.3f})")
    axes[0].scatter([metrics["recall_fraud"]], [metrics["precision_fraud"]], color="#d1242f", zorder=3, label="Seuil retenu")
    axes[0].set(title=f"Précision-rappel (AP = {metrics['average_precision']})", xlabel="Rappel", ylabel="Précision")
    axes[0].legend()
    matrix = metrics["confusion_matrix"]
    values = np.array([[matrix["tn"], matrix["fp"]], [matrix["fn"], matrix["tp"]]])
    axes[1].imshow(values, cmap="Blues", norm=matplotlib.colors.LogNorm())
    axes[1].set(title="Matrice de confusion (test)", xticks=[0, 1], yticks=[0, 1],
                xticklabels=["Prédit légitime", "Prédit fraude"], yticklabels=["Légitime", "Fraude"])
    for (row, column), value in np.ndenumerate(values):
        axes[1].text(column, row, str(value), ha="center", va="center", fontsize=13)
    figure.tight_layout()
    figure.savefig(RESULTS / "evaluation.png", dpi=130)


def main():
    path = Path(sys.argv[1]) if len(sys.argv) > 1 else HERE / "bank_transactions.csv"
    RESULTS.mkdir(exist_ok=True)

    # 1-2. Loading and exploration
    data, synthetic = load_data(path)
    print(f"Rows : {len(data)}{'  (SYNTHETIC DATA)' if synthetic else ''}")
    print(f"Fraud proportion : {data[TARGET].mean():.4f}")
    print("Missing values :\n" + data.isna().sum().to_string())

    # 3-4. Stratified split, identifiers excluded from the features
    X = data.drop(columns=[TARGET] + IDENTIFIERS)
    y = data[TARGET].to_numpy()
    X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, stratify=y, random_state=SEED)
    print(f"Train : {len(X_train)} rows, {y_train.mean():.4f} fraud | Test : {len(X_test)} rows, {y_test.mean():.4f} fraud")

    # 5-6. Model and threshold chosen by cross-validation on the training set only
    folds = StratifiedKFold(n_splits=5, shuffle=True, random_state=SEED)
    candidates = {}
    for name, model in build_models().items():
        scores = cross_val_predict(model, X_train, y_train, cv=folds, method="predict_proba", n_jobs=1)[:, 1]
        candidates[name] = {"cv_average_precision": average_precision_score(y_train, scores), "scores": scores}
        print(f"{name} : cross-validated average precision = {candidates[name]['cv_average_precision']:.4f}")
    best_name = max(candidates, key=lambda name: candidates[name]["cv_average_precision"])
    threshold = choose_threshold(y_train, candidates[best_name]["scores"])
    print(f"Selected : {best_name}, threshold = {threshold:.4f}")

    # 7. Final evaluation : the test set is used here for the first and only time
    model = build_models()[best_name].fit(X_train, y_train)
    test_scores = model.predict_proba(X_test)[:, 1]
    metrics = evaluate(y_test, test_scores, threshold)
    print("Test metrics :\n" + json.dumps(metrics, indent=2))

    # Effect of the threshold, reported for the analysis only : it does not influence any choice above
    sensitivity = {f"{value:.2f}": evaluate(y_test, test_scores, value) for value in (0.1, 0.3, 0.5, 0.7, 0.9)}
    print("Threshold sensitivity (test, informative only) :")
    for value, result in sensitivity.items():
        print(f"  {value} : precision={result['precision_fraud']} recall={result['recall_fraud']} "
              f"fp={result['confusion_matrix']['fp']} fn={result['confusion_matrix']['fn']}")

    report = {"synthetic_data": synthetic, "rows": len(data), "fraud_rate": round(float(data[TARGET].mean()), 4),
              "missing_values": {k: int(v) for k, v in data.isna().sum().items() if v},
              "cv_average_precision": {k: round(v["cv_average_precision"], 4) for k, v in candidates.items()},
              "selected_model": best_name, "test_metrics": metrics, "threshold_sensitivity": sensitivity}
    (RESULTS / "metrics.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    save_plots(y_test, test_scores, metrics)
    print(f"Results written to {RESULTS}")


if __name__ == "__main__":
    main()
