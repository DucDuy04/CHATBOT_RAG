import { Bot, Headphones, MessageSquare, FileText, Zap } from 'lucide-react';

export const mockChatbotData = {
  // Dữ liệu bảng
  list:[
    { 
      id: 1, 
      name: 'HR Bot', 
      docsLinked: 30, 
      createdDate: 'Oct 12, 2023', 
      icon: Bot, 
      iconBg: 'bg-blue-100', 
      iconColor: 'text-blue-600' 
    },
    { 
      id: 2, 
      name: 'Support Bot', 
      docsLinked: 100, 
      createdDate: 'Nov 05, 2023', 
      icon: Headphones, 
      iconBg: 'bg-green-100', 
      iconColor: 'text-green-600' 
    }
  ],
  // Dữ liệu 3 thẻ thống kê bên dưới
  stats:[
    { id: 1, title: 'Total Queries', value: '12,482', icon: MessageSquare, iconBg: 'bg-blue-50', iconColor: 'text-blue-600' },
    { id: 2, title: 'Total Knowledge Docs', value: '1,240', icon: FileText, iconBg: 'bg-blue-50', iconColor: 'text-blue-600' },
    { id: 3, title: 'Avg. Response Time', value: '1.2s', icon: Zap, iconBg: 'bg-blue-50', iconColor: 'text-blue-600' },
  ]
};