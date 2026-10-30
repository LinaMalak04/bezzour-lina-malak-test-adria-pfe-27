import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface Account {
  id: string;
  customerReference: string;
  status: 'ACTIVE' | 'BLOCKED';
  currency: string;
  balance: number;
}

export interface Transaction {
  id: string;
  accountId: string;
  amount: number;
  currency: string;
  merchantCategory: string;
  country: string;
  createdAt: string;
  type: 'DEBIT' | 'CREDIT';
  status: 'PENDING' | 'COMPLETED' | 'REJECTED';
  failureReason?: string;
}

export interface NewTransaction {
  accountId: string;
  amount: number;
  currency: string;
  merchantCategory: string;
  country: string;
}

export interface NewTransfer {
  sourceAccountId: string;
  targetAccountId: string;
  amount: number;
  currency: string;
}

// Every call goes through the gateway (proxied by ng serve in development and by nginx in Docker)
@Injectable({ providedIn: 'root' })
export class BankApiService {
  private readonly http = inject(HttpClient);

  accounts(): Observable<Account[]> {
    return this.http.get<Account[]>('/api/accounts');
  }

  changeStatus(id: string, status: Account['status']): Observable<Account> {
    return this.http.patch<Account>(`/api/accounts/${id}/status`, { status });
  }

  transactions(accountId: string): Observable<Transaction[]> {
    return this.http.get<Transaction[]>('/api/transactions', { params: { accountId } });
  }

  createTransaction(transaction: NewTransaction): Observable<Transaction> {
    return this.http.post<Transaction>('/api/transactions', transaction);
  }

  transfer(transfer: NewTransfer): Observable<Transaction[]> {
    return this.http.post<Transaction[]>('/api/transactions/transfers', transfer);
  }

  chat(conversationId: string, message: string): Observable<{ reply: string }> {
    return this.http.post<{ reply: string }>('/api/chat', { conversationId, message });
  }
}
