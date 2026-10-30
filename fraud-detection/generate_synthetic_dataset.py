"""Generates a SYNTHETIC bank_transactions.csv with the columns of the subject.

Only used when the real file is not available. The results obtained on it illustrate the method
and say nothing about real fraud.
"""
import sys

import numpy as np
import pandas as pd

COUNTRIES = ["MA", "FR", "ES", "DE", "US", "AE", "NG"]
HOME_WEIGHTS = [0.78, 0.09, 0.05, 0.03, 0.02, 0.02, 0.01]
FRAUD_WEIGHTS = [0.30, 0.12, 0.08, 0.05, 0.15, 0.12, 0.18]
CATEGORIES = ["GROCERY", "FUEL", "RESTAURANT", "TRAVEL", "ELECTRONICS", "ONLINE", "CASH"]
HOME_CATEGORY_WEIGHTS = [0.32, 0.14, 0.20, 0.06, 0.05, 0.18, 0.05]
FRAUD_CATEGORY_WEIGHTS = [0.06, 0.04, 0.05, 0.15, 0.25, 0.30, 0.15]


def generate(n=20000, fraud_rate=0.02, seed=42):
    rng = np.random.default_rng(seed)
    is_fraud = (rng.random(n) < fraud_rate).astype(int)
    fraud = is_fraud == 1

    average_7d = rng.lognormal(mean=4.0, sigma=0.6, size=n)
    # Fraud amounts overlap with normal ones on purpose : the task must not be trivially separable
    ratio = np.where(fraud, rng.lognormal(0.7, 0.8, n), rng.lognormal(-0.1, 0.5, n))
    hour = np.where(fraud, rng.choice(24, n, p=_night_profile()), rng.choice(24, n, p=_day_profile()))
    frame = pd.DataFrame({
        "transaction_id": [f"T{i:07d}" for i in range(n)],
        "account_id": [f"A{value:05d}" for value in rng.integers(0, 3000, n)],
        "amount": np.round(average_7d * ratio, 2),
        "hour": hour,
        "country": np.where(fraud, rng.choice(COUNTRIES, n, p=FRAUD_WEIGHTS), rng.choice(COUNTRIES, n, p=HOME_WEIGHTS)),
        "merchant_category": np.where(fraud, rng.choice(CATEGORIES, n, p=FRAUD_CATEGORY_WEIGHTS),
                                      rng.choice(CATEGORIES, n, p=HOME_CATEGORY_WEIGHTS)),
        "transactions_last_24h": np.where(fraud, rng.poisson(5, n), rng.poisson(2.5, n)),
        "average_amount_last_7d": np.round(average_7d, 2),
        "is_fraud": is_fraud,
    })
    for column, share in {"country": 0.02, "merchant_category": 0.03, "average_amount_last_7d": 0.04}.items():
        frame.loc[rng.random(n) < share, column] = np.nan
    return frame


def _day_profile():
    weights = np.array([1, 1, 1, 1, 1, 2, 4, 6, 8, 9, 9, 10, 11, 10, 9, 9, 9, 10, 10, 9, 7, 5, 3, 2], dtype=float)
    return weights / weights.sum()


def _night_profile():
    weights = np.array([8, 9, 9, 8, 7, 5, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 5, 6, 7], dtype=float)
    return weights / weights.sum()


if __name__ == "__main__":
    path = sys.argv[1] if len(sys.argv) > 1 else "bank_transactions.csv"
    generate().to_csv(path, index=False)
    print(f"Synthetic dataset written to {path}")
