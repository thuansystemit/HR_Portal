import { Component, input } from '@angular/core';
import { NgClass } from '@angular/common';
import { CategoryRolePermission, CATEGORY_PERM_COLS } from '../../models/document.model';

@Component({
  selector: 'app-category-perm-cell',
  imports: [NgClass],
  templateUrl: './category-perm-cell.html',
  styleUrl: './category-perm-cell.scss',
})
export class CategoryPermCell {
  permissions = input.required<CategoryRolePermission[]>();
  protected readonly cols = CATEGORY_PERM_COLS;
}
